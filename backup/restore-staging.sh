#!/usr/bin/env bash
# S4-10 Lát 4: phục hồi staging từ một tệp sao lưu và đo thời gian đến khi hệ thống chạy lại.
# Chạy ở thư mục gốc repo: bash backup/restore-staging.sh homestay_YYYYMMDD_HHMMSS.dump [--chi-kiem-tra]
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."

FILE="${1:?Can ten tep, vi du: bash backup/restore-staging.sh homestay_20261018_020000.dump}"
MODE="${2:-}"

if [ "$MODE" = "--chi-kiem-tra" ]; then
  docker compose exec -T backup restore.sh "$FILE" --chi-kiem-tra
  exit $?
fi

START=$(date +%s)
echo ">> Bat dau luc $(date '+%H:%M:%S'). Dung backend..."
docker compose stop backend

echo ">> Phuc hoi co so du lieu tu $FILE"
if ! docker compose exec -T backup restore.sh "$FILE"; then
  echo ">> Phuc hoi THAT BAI, CSDL cu giu nguyen. Bat lai backend."
  docker compose start backend
  exit 1
fi

echo ">> Bat lai backend, cho den khi san sang..."
docker compose start backend
CID=$(docker compose ps -q backend)
until [ "$(docker inspect -f '{{.State.Health.Status}}' "$CID")" = "healthy" ]; do
  sleep 2
done

SECS=$(( $(date +%s) - START ))
MSG="$(date '+%Y-%m-%d %H:%M:%S %z') | XONG | $FILE | he thong chay lai sau $((SECS / 60)) phut $((SECS % 60)) giay"
docker compose exec -T backup sh -c "echo '$MSG' >> /backups/restore.log"
echo "$MSG"
echo ">> Mo http://localhost:8088/dang-nhap, dang nhap lai va ghi thoi diem dang nhap duoc vao bien ban."