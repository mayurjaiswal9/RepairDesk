#!/bin/bash
# RepairDesk — Database & uploads backup script (REL-002).
# Run manually or schedule via cron:
#   0 2 * * * /opt/repairdesk/deploy/scripts/backup.sh >> /var/log/repairdesk-backup.log 2>&1

set -euo pipefail

# ── Config ─────────────────────────────────────────────────────────────────
BACKUP_DIR="${BACKUP_DIR:-/opt/repairdesk/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
COMPOSE_FILE="${COMPOSE_FILE:-/opt/repairdesk/deploy/docker-compose.prod.yml}"
DATE=$(date +"%Y%m%d_%H%M%S")

# Load env vars (DB_USER, DB_NAME, DB_PASSWORD)
if [ -f /opt/repairdesk/deploy/.env ]; then
    set -a && source /opt/repairdesk/deploy/.env && set +a
fi

mkdir -p "$BACKUP_DIR/db"
mkdir -p "$BACKUP_DIR/uploads"

echo "[$(date -u)] Starting RepairDesk backup..."

# ── Database dump ───────────────────────────────────────────────────────────
echo "[$(date -u)] Dumping PostgreSQL database..."
docker compose -f "$COMPOSE_FILE" exec -T db \
    pg_dump -U "${DB_USER:-repairdesk}" "${DB_NAME:-repairdesk}" \
    | gzip > "$BACKUP_DIR/db/repairdesk_${DATE}.sql.gz"
echo "[$(date -u)] Database dump saved: repairdesk_${DATE}.sql.gz"

# ── Uploads backup ──────────────────────────────────────────────────────────
echo "[$(date -u)] Backing up uploads volume..."
docker run --rm \
    -v repairdesk_uploads:/data:ro \
    -v "$BACKUP_DIR/uploads":/backup \
    alpine \
    tar czf "/backup/uploads_${DATE}.tar.gz" -C /data .
echo "[$(date -u)] Uploads backup saved: uploads_${DATE}.tar.gz"

# ── Prune old backups ───────────────────────────────────────────────────────
echo "[$(date -u)] Pruning backups older than ${RETENTION_DAYS} days..."
find "$BACKUP_DIR" -name "*.gz" -mtime +"$RETENTION_DAYS" -delete
echo "[$(date -u)] Backup complete."
