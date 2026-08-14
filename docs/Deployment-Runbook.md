# RepairDesk V1 — Deployment & Operations Runbook (DOC-003)

> **Azure deployment:** For hosting the backend on **Azure Container Apps** (with PostgreSQL, ACR, secrets, and security configuration), see [Azure-Container-Apps-Deployment.md](Azure-Container-Apps-Deployment.md).

## Overview

RepairDesk runs as a set of Docker Compose services on a single Ubuntu 22.04 LTS server:

| Service | Image | Port |
|---------|-------|------|
| `db` | `postgres:16-alpine` | 5432 (internal) |
| `api` | `repairdesk-api` (custom) | 8080 (internal) |
| `nginx` | `nginx:1.27-alpine` | 80, 443 (public) |
| `certbot` | `certbot/certbot` | — |

---

## Prerequisites

- Ubuntu 22.04+ server with at least 2 GB RAM, 20 GB disk
- Domain name pointing to the server's IP (A record)
- Docker 24+ and Docker Compose V2 installed
- Ports 80 and 443 open in firewall

### Install Docker on Ubuntu

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# Log out and back in for group change to take effect
```

---

## First-Time Setup (REL-001)

### 1. Clone the repository

```bash
git clone <your-repo-url> /opt/repairdesk
cd /opt/repairdesk
```

### 2. Create the environment file

```bash
cp deploy/env.example deploy/.env
nano deploy/.env   # Fill in DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD
```

**Generate a strong JWT secret:**

```bash
openssl rand -hex 32
```

### 3. Configure Nginx

```bash
# Replace YOUR_DOMAIN in the nginx config
sed -i 's/YOUR_DOMAIN/repairdesk.yourdomain.com/g' deploy/nginx/conf.d/repairdesk.conf
```

### 4. Obtain TLS certificate (Let's Encrypt)

```bash
# Start nginx in HTTP-only mode first (comment out the 443 server block temporarily)
docker compose -f deploy/docker-compose.prod.yml up -d nginx certbot

# Request certificate
docker compose -f deploy/docker-compose.prod.yml run --rm certbot \
  certonly --webroot -w /var/www/certbot \
  --email admin@yourdomain.com \
  -d repairdesk.yourdomain.com \
  --agree-tos --non-interactive

# Uncomment the 443 block and restart nginx
docker compose -f deploy/docker-compose.prod.yml restart nginx
```

### 5. Build and start all services

```bash
cd /opt/repairdesk
docker compose -f deploy/docker-compose.prod.yml build api
docker compose -f deploy/docker-compose.prod.yml up -d
```

### 6. Verify startup

```bash
# Check all services are healthy
docker compose -f deploy/docker-compose.prod.yml ps

# Check API health
curl -sf https://repairdesk.yourdomain.com/actuator/health | python3 -m json.tool
```

Expected response: `{"status": "UP"}`

---

## Updating the Application (REL-003)

```bash
cd /opt/repairdesk
git pull

# Rebuild API image
docker compose -f deploy/docker-compose.prod.yml build api

# Rolling restart (zero downtime — Flyway migrations run on startup)
docker compose -f deploy/docker-compose.prod.yml up -d --no-deps api
```

---

## Backup & Restore (REL-002)

### Manual backup

```bash
chmod +x deploy/scripts/backup.sh
./deploy/scripts/backup.sh
```

Backups are saved to `/opt/repairdesk/backups/`.

### Automated backups via cron

```bash
sudo crontab -e
# Add the following line (runs at 2:00 AM every day):
0 2 * * * /opt/repairdesk/deploy/scripts/backup.sh >> /var/log/repairdesk-backup.log 2>&1
```

### Restore from backup

```bash
# Restore database only
./deploy/scripts/restore.sh backups/db/repairdesk_20260628_020000.sql.gz

# Restore database + uploads
./deploy/scripts/restore.sh \
  backups/db/repairdesk_20260628_020000.sql.gz \
  backups/uploads/uploads_20260628_020000.tar.gz
```

---

## Logs

```bash
# API logs (live)
docker compose -f deploy/docker-compose.prod.yml logs -f api

# Nginx access log
docker compose -f deploy/docker-compose.prod.yml logs -f nginx

# All services
docker compose -f deploy/docker-compose.prod.yml logs -f
```

---

## Smoke Test Checklist (REL-004)

Run these checks after every deployment:

- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] `POST /auth/login` with admin credentials returns a JWT token
- [ ] `GET /dashboard/counters` with the JWT token returns counters JSON
- [ ] Swagger UI loads at `https://your-domain/swagger-ui/index.html`
- [ ] Flutter mobile app (internal build) can log in and view the dashboard
- [ ] Create a new repair request via the app
- [ ] Admin can view and approve the request
- [ ] Upload a photo attachment; verify it appears in the job detail

---

## Common Operations

### Reset admin password

```bash
# Connect to the DB
docker compose -f deploy/docker-compose.prod.yml exec db \
  psql -U repairdesk repairdesk

-- Inside psql (replace $HASH with a bcrypt-hashed password):
UPDATE users
SET password_hash = '$HASH', must_change_password = true
WHERE username = 'admin';
\q
```

Generate a bcrypt hash online (cost factor 12) or via:

```bash
python3 -c "import bcrypt; print(bcrypt.hashpw(b'NewPass@1234', bcrypt.gensalt(rounds=12)).decode())"
```

### Scale workers (future)

For V1 a single container is sufficient. If CPU becomes a bottleneck, increase Tomcat threads:

```yaml
# In docker-compose.prod.yml under api environment:
SERVER_TOMCAT_THREADS_MAX: "50"
```

### Monitor disk usage

```bash
df -h
du -sh /opt/repairdesk/backups
docker system df   # Docker images/volumes
```

---

## Security Checklist (SEC-004)

- [ ] Change default admin password immediately after first login
- [ ] `ADMIN_PASSWORD` removed from `.env` once the account password has been changed via the app
- [ ] `JWT_SECRET` is at least 32 random characters and not committed to git
- [ ] Port 5432 (PostgreSQL) is NOT exposed to the public internet
- [ ] Automated backups are enabled and backup files are stored off-server
- [ ] TLS certificate auto-renewal is working (`certbot renew --dry-run`)
- [ ] `app.storage.location` volume is not accessible via the public internet directly (Nginx blocks `/uploads/` path)

---

## Contacts / Escalation

| Role | Contact |
|------|---------|
| System Admin | _add contact here_ |
| Application Owner | _add contact here_ |
