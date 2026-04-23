# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.0] - 2026-04-24

### Fixed
- Vite dev server lacked proxy configuration, causing `/api` requests
  to fail when running `npm run dev` (requests hit port 5173 instead of 8080)
- MariaDB backup failing with "No backup strategy registered for database type: MARIADB"
- Database listing and size queries failing in MariaDB containers where only
  `mariadb` CLI is available and `mysql` is not present

### Changed
- S3 connection check now verifies write access by uploading a small probe file
  (`backup-manager-probe.tmp`) instead of using HeadBucket, which required
  read permissions that many bucket policies don't grant
- S3 connectivity is re-checked every 15 minutes instead of every 5

### Added
- "Check" button in Settings → AWS S3 to test connectivity immediately
  without saving, using the current form values
- Compression toggle per backup task - gzip enabled by default (.sql.gz);
  applies to both PostgreSQL (gzip pipe) and MySQL
- "Upload to S3" toggle per backup task - enabled by default;
  allows disabling S3 upload for specific tasks without touching global settings
- Run Now and Enable/Disable buttons on task detail page
- Inline task editing on task detail page — name, schedule, compression,
  upload to S3, and keep backups count
- "Check" button in Settings → Email to test SMTP connectivity before saving
- "Check" button in Settings → Heartbeat to test ping URL before saving
- Database size displayed next to each database name in the backup task wizard
    (fetched asynchronously, cached for 30 minutes)

---

## [0.1.2] - 2026-04-20

### Fixed
- API requests used hardcoded `http://localhost:8080` base URL,
  causing CORS failure when app is accessed by server IP or domain

---

## [0.1.1] - 2026-04-19

### Fixed
- Backup directory creation failing when mounted volume is owned by root - container now runs as root for full filesystem access
- Version number corrected in package.json and Sidebar UI (was 0.0.0 / 1.0.0)
- Update docker-socket-manager version from 0.1.0 to 0.1.1

---

## [0.1.0] - 2026-04-18

### Added
- Docker socket management - local Unix socket and remote SSH-tunneled connections
- Auto-discovery of PostgreSQL, MySQL, and MariaDB containers
- Scheduled backups via cron expressions or fixed-delay intervals
- Retention policy - keep last N backups, old files deleted automatically
- Disk space pre-flight check before each backup
- Optional S3-compatible storage upload after each backup
- Email notifications on success and/or failure via SMTP
- Container resurrection - re-resolves container IDs by name after docker-compose restarts
- Heartbeat pings to UptimeRobot, Betterstack, and similar uptime monitoring services
- Backup history log with status, duration, and file size
- Built-in authentication with session-based login and remember me (30 days)