#!/usr/bin/env bash
# EC2 에서 최초 1회: Let's Encrypt 인증서 발급 + 자동 갱신 설정.
# 사용: bash https-setup.sh [도메인]   (기본 expohub.duckdns.org)
#
# 방식: certbot standalone. 발급·갱신하는 몇 초 동안 web 컨테이너를 멈춰 80 포트를 비워 준다.
# 갱신은 certbot 이 설치하는 systemd 타이머가 하루 2번 확인하고, 만료 30일 전에만 실제로 갱신한다.
# 전제: 보안 그룹에서 80·443 인바운드가 열려 있고, 도메인이 이 인스턴스 IP 를 가리킨다.
set -euo pipefail

DOMAIN="${1:-expohub.duckdns.org}"
COMPOSE="docker compose -f $HOME/fpt1/docker-compose.prod.yml"

sudo apt-get update -y
sudo apt-get install -y certbot

sudo certbot certonly --standalone \
  -d "$DOMAIN" \
  --non-interactive --agree-tos --register-unsafely-without-email \
  --pre-hook "$COMPOSE stop web || true" \
  --post-hook "$COMPOSE start web || true"

# 위 hook 은 갱신 설정(/etc/letsencrypt/renewal/$DOMAIN.conf)에 저장돼 자동 갱신 때도 그대로 쓰인다
sudo certbot renew --dry-run
systemctl list-timers | grep -i certbot || true

echo "완료: /etc/letsencrypt/live/$DOMAIN/ 에 인증서가 있다. 이제 443 설정이 들어간 버전을 배포하면 된다."
