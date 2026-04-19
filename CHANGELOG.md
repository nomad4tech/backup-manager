# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.1] - 2026-04-19

### Fixed
- Backup directory creation failing when mounted volume is owned by root - container now runs entrypoint as root, fixes permissions, then switches to appuser
- Version number corrected in package.json and Sidebar UI (was 0.0.0 / 1.0.0)

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