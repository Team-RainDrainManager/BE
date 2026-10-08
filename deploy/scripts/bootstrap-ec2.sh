#!/usr/bin/env bash
# EC2(Ubuntu 24.04) 최초 1회 서버 설정. 여러 번 실행해도 안전하다(idempotent).
#
# 사용법: sudo bash bootstrap-ec2.sh <도메인> [--force-nginx]
#   --force-nginx  기존 Nginx 설정을 백업하고 레포 템플릿으로 덮어쓴다.
#                  인증서가 이미 있으면 certbot install로 HTTPS 설정을 복원한다.
set -euo pipefail

APP_DIR="/opt/rain-butler"
APP_USER="ubuntu"
NGINX_TEMPLATE_URL="${NGINX_TEMPLATE_URL:-https://raw.githubusercontent.com/Team-RainDrainManager/BE/main/deploy/nginx/rain-butler.conf}"
NGINX_SITE="/etc/nginx/sites-available/rain-butler"

log() {
    echo "[bootstrap] $*"
}

usage() {
    echo "사용법: sudo bash $0 <도메인> [--force-nginx]" >&2
    exit 1
}

# ───────── 1. 인자 · 권한 확인 ─────────
DOMAIN=""
FORCE_NGINX=false
for arg in "$@"; do
    case "$arg" in
        --force-nginx) FORCE_NGINX=true ;;
        -*) usage ;;
        *) DOMAIN="$arg" ;;
    esac
done
[[ -n "$DOMAIN" ]] || usage
# sed 치환에 쓰이므로 도메인 형식만 허용한다
if [[ ! "$DOMAIN" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]]; then
    echo "도메인 형식이 올바르지 않습니다: $DOMAIN" >&2
    exit 1
fi
if [[ "$(id -u)" -ne 0 ]]; then
    echo "root 권한이 필요합니다. sudo로 실행하세요." >&2
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive
APT_OPTS=(-y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)

# ───────── 2. 패키지 업데이트 · 자동 보안 업데이트 ─────────
log "패키지 업데이트"
apt-get update
apt-get "${APT_OPTS[@]}" upgrade
apt-get "${APT_OPTS[@]}" install unattended-upgrades ca-certificates curl gnupg
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

# ───────── 3. 시간대 ─────────
log "시간대 Asia/Seoul"
timedatectl set-timezone Asia/Seoul

# ───────── 4. swap 2GB ─────────
if [[ -f /swapfile ]]; then
    log "swap 파일이 이미 있어 건너뜀"
else
    log "swap 2GB 생성"
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
fi
if ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
    swapon /swapfile
fi
if ! grep -q '^/swapfile ' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/99-rain-butler.conf
sysctl --system > /dev/null

# ───────── 5. Docker Engine + compose plugin (공식 apt 저장소) ─────────
if command -v docker > /dev/null && docker compose version > /dev/null 2>&1; then
    log "Docker가 이미 설치되어 건너뜀"
else
    log "Docker 설치"
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    # shellcheck source=/dev/null
    CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${CODENAME} stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get "${APT_OPTS[@]}" install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
usermod -aG docker "$APP_USER"

# ───────── 6. Nginx · certbot ─────────
log "Nginx · certbot 설치"
apt-get "${APT_OPTS[@]}" install nginx certbot python3-certbot-nginx
systemctl enable --now nginx

# ───────── 7. 앱 디렉터리 ─────────
log "앱 디렉터리 ${APP_DIR}"
install -d -o "$APP_USER" -g "$APP_USER" "$APP_DIR" "$APP_DIR/backups"

# ───────── 7-1. DB 백업 cron (매일 03:00) ─────────
CRON_LINE="0 3 * * * ${APP_DIR}/backup-db.sh >> ${APP_DIR}/backups/backup.log 2>&1"
CURRENT_CRON="$(crontab -u "$APP_USER" -l 2>/dev/null || true)"
if grep -qF "${APP_DIR}/backup-db.sh" <<< "$CURRENT_CRON"; then
    log "백업 cron이 이미 있어 건너뜀"
else
    log "백업 cron 등록 (매일 03:00)"
    printf '%s\n%s\n' "$CURRENT_CRON" "$CRON_LINE" | sed '/^$/d' | crontab -u "$APP_USER" -
fi

# ───────── 8. Nginx 설정 배치 ─────────
install_nginx_template() {
    local script_dir template
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    template="$(mktemp)"
    # 같은 디렉터리(또는 레포의 deploy/nginx)에 템플릿이 있으면 쓰고, 없으면 GitHub에서 받는다
    if [[ -f "$script_dir/rain-butler.conf" ]]; then
        cp "$script_dir/rain-butler.conf" "$template"
    elif [[ -f "$script_dir/../nginx/rain-butler.conf" ]]; then
        cp "$script_dir/../nginx/rain-butler.conf" "$template"
    else
        log "Nginx 템플릿 다운로드: $NGINX_TEMPLATE_URL"
        curl -fsSL "$NGINX_TEMPLATE_URL" -o "$template"
    fi
    sed "s/__DOMAIN__/${DOMAIN}/g" "$template" > "$NGINX_SITE"
    rm -f "$template"
}

if [[ -f "$NGINX_SITE" && "$FORCE_NGINX" != true ]]; then
    # certbot이 추가한 443 설정이 지워지지 않도록 기존 파일은 건드리지 않는다
    log "Nginx 설정이 이미 있어 건너뜀 (덮어쓰려면 --force-nginx)"
else
    if [[ -f "$NGINX_SITE" ]]; then
        BACKUP="${NGINX_SITE}.bak.$(date +%Y%m%d-%H%M%S)"
        # sites-available 안에 두면 include 대상이 아니므로 안전하다
        cp "$NGINX_SITE" "$BACKUP"
        log "기존 Nginx 설정 백업: $BACKUP"
    fi
    log "Nginx 설정 배치: $NGINX_SITE"
    install_nginx_template
    ln -sf "$NGINX_SITE" /etc/nginx/sites-enabled/rain-butler
    rm -f /etc/nginx/sites-enabled/default

    # 인증서가 이미 있으면(재실행) 템플릿에 없는 HTTPS 설정을 복원한다
    if [[ -d "/etc/letsencrypt/live/${DOMAIN}" ]]; then
        log "기존 인증서로 HTTPS 설정 복원"
        nginx -t
        certbot install --nginx --cert-name "$DOMAIN" --redirect --non-interactive
    fi
fi
nginx -t
systemctl reload nginx

# ───────── 9. 다음 할 일 ─────────
cat <<EOF

[bootstrap] 완료. 다음 할 일:
  1) .env 작성:   sudo nano ${APP_DIR}/.env   (레포 deploy/env.example 참고)
                  sudo chown ${APP_USER}:${APP_USER} ${APP_DIR}/.env && sudo chmod 600 ${APP_DIR}/.env
  2) HTTPS 발급:  sudo certbot --nginx -d ${DOMAIN} -m <이메일> --agree-tos --redirect
  3) GitHub Secrets 등록: EC2_HOST, EC2_USER, EC2_SSH_KEY
  ※ docker 그룹 권한은 ${APP_USER} 재로그인 후 적용됩니다.
EOF
