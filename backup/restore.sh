#!/bin/sh
# S4-10 Lát 4: dựng lại cơ sở dữ liệu từ một tệp sao lưu.
# Cách dùng: restore.sh <tên tệp .dump> [--chi-kiem-tra]
#   --chi-kiem-tra: chỉ phục hồi vào CSDL tạm để đối chiếu số dòng, không đụng CSDL đang chạy.
# Phục hồi vào CSDL tạm trước; chỉ khi thành công mới đổi tên thay CSDL đang dùng,
# CSDL cũ được giữ lại với tên <DB_NAME>_truoc_phuc_hoi để quay về nếu cần.
set -u

BACKUP_DIR=/backups
LOG="$BACKUP_DIR/restore.log"
export PGHOST="$DB_HOST" PGUSER="$DB_USERNAME" PGPASSWORD="$DB_PASSWORD"
# Ẩn các thông báo NOTICE vô hại của PostgreSQL
export PGOPTIONS="-c client_min_messages=warning"
NAME="${1:-}"
MODE="${2:-}"
FILE="$BACKUP_DIR/$(basename "$NAME")"
TMP_DB="${DB_NAME}_restore"
OLD_DB="${DB_NAME}_truoc_phuc_hoi"

umask 077

now() {
  date '+%Y-%m-%d %H:%M:%S %z'
}

log() {
  echo "$1" | tee -a "$LOG"
}

# Số dòng các bảng chính để đối chiếu trước và sau khi phục hồi
counts() {
  psql -d "$1" -Atc "SELECT 'tai_khoan=' || (SELECT count(*) FROM users)
    || ' loai_phong=' || (SELECT count(*) FROM room_types)
    || ' phong=' || (SELECT count(*) FROM rooms)
    || ' booking=' || (SELECT count(*) FROM bookings)
    || ' hoa_don=' || (SELECT count(*) FROM issued_invoices)"
}

if [ -z "$NAME" ] || [ ! -f "$FILE" ]; then
  echo "Cach dung: restore.sh <ten tep .dump> [--chi-kiem-tra]"
  echo "Cac ban sao luu hien co:"
  ls -1 "$BACKUP_DIR"/homestay_*.dump 2>/dev/null | xargs -n1 basename
  exit 2
fi

log "$(now) | BAT_DAU | $(basename "$FILE") | $(du -h "$FILE" | cut -f1) | ${MODE:-phuc-hoi-that}"
log "$(now) | TRUOC | $(counts "$DB_NAME")"

psql -d postgres -qc "DROP DATABASE IF EXISTS \"$TMP_DB\""
createdb "$TMP_DB"
if ! pg_restore --no-owner --exit-on-error -d "$TMP_DB" "$FILE" 2>/tmp/restore.err; then
  log "$(now) | THAT_BAI | $(tr '\n' ' ' < /tmp/restore.err | cut -c1-300) | CSDL dang chay giu nguyen"
  dropdb --if-exists "$TMP_DB"
  exit 1
fi
log "$(now) | SAU | $(counts "$TMP_DB")"

if [ "$MODE" = "--chi-kiem-tra" ]; then
  dropdb "$TMP_DB"
  log "$(now) | KIEM_TRA_XONG | CSDL dang chay khong thay doi"
  exit 0
fi

if ! psql -d postgres -v ON_ERROR_STOP=1 -q >/dev/null <<SQL
DROP DATABASE IF EXISTS "$OLD_DB";
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();
ALTER DATABASE "$DB_NAME" RENAME TO "$OLD_DB";
ALTER DATABASE "$TMP_DB" RENAME TO "$DB_NAME";
SQL
then
  log "$(now) | THAT_BAI | khong doi ten duoc CSDL, kiem tra $DB_NAME / $OLD_DB / $TMP_DB"
  exit 1
fi

log "$(now) | DA_THAY_CSDL | ban cu giu o $OLD_DB"
exit 0