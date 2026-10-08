#!/usr/bin/env bash
# 서버 DB 컨테이너(rain-butler-db)를 pg_dump로 백업한다. 7일 지난 백업은 지운다.
# cron(매일 03:00)과 수동 실행 모두 사용한다. CD가 매 배포 때 서버에 복사한다.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/rain-butler}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
BACKUP_DIR="${BACKUP_DIR:-$APP_DIR/backups}"
DB_CONTAINER="${DB_CONTAINER:-rain-butler-db}"
RETENTION_DAYS=7

log() {
    echo "[backup] $(date '+%F %T') $*"
}

# .env에서 값 하나를 읽는다 (source 하지 않는다 - 비밀번호 특수문자 대비)
read_env() {
    grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true
}

if [[ ! -f "$ENV_FILE" ]]; then
    log "$ENV_FILE 이 없습니다"
    exit 1
fi
POSTGRES_USER="$(read_env POSTGRES_USER)"
POSTGRES_DB="$(read_env POSTGRES_DB)"
if [[ -z "$POSTGRES_USER" || -z "$POSTGRES_DB" ]]; then
    log ".env에 POSTGRES_USER / POSTGRES_DB가 없습니다"
    exit 1
fi

# RDS 이전 후에는 컨테이너가 없으므로 정상 종료한다
if [[ "$(docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null || true)" != "true" ]]; then
    log "$DB_CONTAINER 컨테이너가 실행 중이 아닙니다 (RDS로 이전됨 또는 DB 중지). 백업 건너뜀"
    exit 0
fi

mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/rainbutler-$(date +%Y%m%d-%H%M).dump"

log "백업 시작: $FILE"
docker exec "$DB_CONTAINER" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$FILE"

if [[ ! -s "$FILE" ]]; then
    rm -f "$FILE"
    log "백업 파일 크기가 0입니다. 실패"
    exit 1
fi
log "백업 완료: $FILE ($(du -h "$FILE" | cut -f1))"

DELETED="$(find "$BACKUP_DIR" -maxdepth 1 -name '*.dump' -type f -mtime +"$RETENTION_DAYS" -print -delete | wc -l)"
log "${RETENTION_DAYS}일 지난 백업 ${DELETED// /}개 삭제"
