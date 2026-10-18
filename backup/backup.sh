#!/bin/sh
# S4-10 Lát 3: sao lưu toàn bộ cơ sở dữ liệu ra tệp nén, chỉ giữ BACKUP_KEEP bản mới nhất.
# Ghi nhật ký vào /backups/backup.log: thời điểm bắt đầu | kết thúc | kết quả | tệp | dung lượng.
set -u

BACKUP_DIR=/backups
KEEP="${BACKUP_KEEP:-7}"
LOG="$BACKUP_DIR/backup.log"

umask 077
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true

log() {
  echo "$1" | tee -a "$LOG"
}

STAMP=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/homestay_${STAMP}.dump"
TMP="$FILE.part"
START=$(date '+%Y-%m-%d %H:%M:%S %z')

# Ghi ra tệp tạm; chỉ khi pg_dump thành công mới đổi tên và dọn bản cũ.
if pg_dump -h "$DB_HOST" -U "$DB_USERNAME" -d "$DB_NAME" -Fc -Z 9 -f "$TMP" 2>"$TMP.err"; then
  mv "$TMP" "$FILE"
  rm -f "$TMP.err"
  SIZE=$(du -h "$FILE" | cut -f1)
  END=$(date '+%Y-%m-%d %H:%M:%S %z')
  log "$START | $END | THANH_CONG | $(basename "$FILE") | $SIZE"

  # Giữ KEEP bản mới nhất (tên tệp chứa ngày giờ nên sắp xếp theo tên là theo thời gian).
  ls -1 "$BACKUP_DIR"/homestay_*.dump 2>/dev/null | sort -r | tail -n +"$((KEEP + 1))" | while read -r OLD; do
    rm -f "$OLD"
    log "$END | XOA_BAN_CU | $(basename "$OLD")"
  done
  exit 0
fi

END=$(date '+%Y-%m-%d %H:%M:%S %z')
REASON=$(tr '\n' ' ' < "$TMP.err" | cut -c1-300)
rm -f "$TMP" "$TMP.err"
log "$START | $END | THAT_BAI | $REASON"
exit 1