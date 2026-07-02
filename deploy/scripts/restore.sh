#!/bin/bash
# RepairDesk — Database restore script (REL-002).
# Usage:
#   ./restore.sh <backup_file.sql.gz>  [<uploads_archive.tar.gz>]
# Example:
#   ./restore.sh backups/db/repairdesk_20260628_020000.sql.gz

set -euo pipefail

DB_DUMP="${1:?Usage: $0 <db_dump.sql.gz> [uploads.tar.gz]}"
UPLOADS_ARCHIVE="${2:-}"
COMPOSE_FILE="${COMPOSE_FILE:-/opt/repairdesk/deploy/docker-compose.prod.yml}"

if [ -f /opt/repairdesk/deploy/.env ]; then
    set -a && source /opt/repairdesk/deploy/.env && set +a
fi

echo "[$(date -u)] !! WARNING: This will OVERWRITE the current database. Continue? [y/N]"
read -r answer
if [[ "${answer}" != "y" && "${answer}" != "Y" ]]; then
    echo "Aborted."
    exit 1
fi

# ── Restore DB ──────────────────────────────────────────────────────────────
echo "[$(date -u)] Restoring database from $DB_DUMP ..."
gunzip -c "$DB_DUMP" | docker compose -f "$COMPOSE_FILE" exec -T db \
    psql -U "${DB_USER:-repairdesk}" "${DB_NAME:-repairdesk}"
echo "[$(date -u)] Database restored."

# ── Restore uploads (optional) ─────────────────────────────────────────────
if [ -n "$UPLOADS_ARCHIVE" ]; then
    echo "[$(date -u)] Restoring uploads from $UPLOADS_ARCHIVE ..."
    docker run --rm \
        -v repairdesk_uploads:/data \
        -v "$(dirname "$UPLOADS_ARCHIVE")":/backup:ro \
        alpine \
        sh -c "rm -rf /data/* && tar xzf /backup/$(basename "$UPLOADS_ARCHIVE") -C /data"
    echo "[$(date -u)] Uploads restored."
fi

echo "[$(date -u)] Restore complete. Restart the API service:"
echo "  docker compose -f $COMPOSE_FILE restart api"
