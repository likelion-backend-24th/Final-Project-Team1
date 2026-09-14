#!/usr/bin/env bash
# EC2(Ubuntu 22.04/24.04) 최초 1회 준비 스크립트.
# 사용: scp 로 올린 뒤 `bash ec2-setup.sh` → 끝나면 재접속(docker 그룹 반영).
set -euo pipefail

# 1) 스왑 4GB — 프리티어 메모리로는 서비스 4개 + MySQL 기동 시 OOM 이 난다
if ! sudo swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 4G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf
sudo sysctl --system > /dev/null

# 2) Docker + compose 플러그인
sudo apt-get update -y
sudo apt-get install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

# 3) 배포 폴더 (compose·설정 파일은 CD 가 복사한다)
mkdir -p ~/fpt1/infra/nginx ~/fpt1/infra/mysql

echo "완료. 로그아웃 후 재접속하고 ~/fpt1/.env 를 채우세요 (infra/deploy/prod.env.example 참고)."
