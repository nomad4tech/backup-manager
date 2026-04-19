#!/bin/sh
chown -R appuser:appgroup /app/backups /data 2>/dev/null || true
exec su-exec appuser java -jar /app/app.jar