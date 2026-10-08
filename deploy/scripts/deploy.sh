#!/usr/bin/env bash
# 새 이미지 태그로 앱을 배포한다. 헬스 체크에 실패하면 이전 태그로 롤백한다.
#
# 사용법(서버, ubuntu 사용자): /opt/rain-butler/deploy.sh <이미지 태그>
# CD가 매 배포 때 이 파일을 서버에 복사하고 실행한다. GHCR 로그인은 호출하는 쪽에서 한다.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/rain-butler}"
COMPOSE_FILE="docker-compose.prod.yml"
APP_CONTAINER="rain-butler"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
HEALTH_TIMEOUT=120
HEALTH_INTERVAL=3

log() {
    echo "[deploy] $(date '+%F %T') $*"
}

# .env에서 값 하나를 읽는다 (source 하지 않는다)
read_env() {
    grep -E "^$1=" .env | tail -n 1 | cut -d= -f2- || true
}

# ───────── 1~2. 인자 · .env 확인 ─────────
if [[ $# -ne 1 || -z "$1" ]]; then
    echo "사용법: $0 <이미지 태그>" >&2
    exit 1
fi
NEW_TAG="$1"
cd "$APP_DIR"

if [[ ! -f .env ]]; then
    log ".env가 없습니다. deploy/env.example을 참고해 ${APP_DIR}/.env를 먼저 작성하세요."
    exit 1
fi
IMAGE_NAME="$(read_env IMAGE_NAME)"
DB_MODE="$(read_env DB_MODE)"
if [[ -z "$IMAGE_NAME" ]]; then
    log ".env에 IMAGE_NAME이 없습니다."
    exit 1
fi

# ───────── 3. 현재 태그 ─────────
PREV_TAG=""
if [[ -f .current_tag ]]; then
    PREV_TAG="$(cat .current_tag)"
fi
log "현재 태그: ${PREV_TAG:-없음} → 새 태그: $NEW_TAG"

# RDS 이전 후(DB_MODE=rds)에는 멈춰 둔 db 컨테이너를 다시 켜지 않도록 app만 올린다
SERVICES=()
if [[ "$DB_MODE" == "rds" ]]; then
    SERVICES=(app)
fi

compose_up() {
    IMAGE_TAG="$1" docker compose -f "$COMPOSE_FILE" up -d "${SERVICES[@]}"
}

# ───────── 4. 이미지 받기 · 실행 ─────────
log "이미지 받기: ${IMAGE_NAME}:${NEW_TAG}"
IMAGE_TAG="$NEW_TAG" docker compose -f "$COMPOSE_FILE" pull app
log "컨테이너 실행 (DB_MODE=${DB_MODE:-container})"
compose_up "$NEW_TAG"

# ───────── 5. 헬스 체크 ─────────
log "헬스 체크 (최대 ${HEALTH_TIMEOUT}초)"
HEALTHY=false
for ((elapsed = 0; elapsed < HEALTH_TIMEOUT; elapsed += HEALTH_INTERVAL)); do
    if curl -fs "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
        HEALTHY=true
        break
    fi
    sleep "$HEALTH_INTERVAL"
done

# ───────── 6. 실패 → 롤백 ─────────
if [[ "$HEALTHY" != true ]]; then
    log "헬스 체크 실패: $NEW_TAG"
    log "최근 로그 100줄"
    docker logs --tail 100 "$APP_CONTAINER" 2>&1 || true
    if [[ -n "$PREV_TAG" && "$PREV_TAG" != "$NEW_TAG" ]]; then
        log "이전 태그로 롤백: $PREV_TAG"
        compose_up "$PREV_TAG" || log "롤백 실행 실패"
    else
        log "롤백할 이전 태그가 없습니다"
    fi
    exit 1
fi

# ───────── 7. 성공 → 태그 기록 · 이미지 정리 ─────────
log "배포 성공: $NEW_TAG"
if [[ -n "$PREV_TAG" && "$PREV_TAG" != "$NEW_TAG" ]]; then
    echo "$PREV_TAG" > .previous_tag
fi
echo "$NEW_TAG" > .current_tag

log "사용하지 않는 이미지 정리"
docker image prune -f > /dev/null || true
KEEP_PREV="$(cat .previous_tag 2>/dev/null || true)"
# 현재 · 이전 태그(와 latest)를 뺀 이 레포 이미지를 지워 디스크를 보호한다
docker image ls "$IMAGE_NAME" --format '{{.Tag}}' | while read -r tag; do
    case "$tag" in
        "$NEW_TAG" | "$KEEP_PREV" | latest | "<none>") ;;
        *)
            log "이미지 삭제: ${IMAGE_NAME}:${tag}"
            docker rmi "${IMAGE_NAME}:${tag}" > /dev/null || true
            ;;
    esac
done
log "완료"
