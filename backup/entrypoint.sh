#!/bin/sh
# S4-10 Lát 3: đặt lịch sao lưu theo BACKUP_SCHEDULE (mặc định 2:00 hằng ngày, giờ Asia/Ho_Chi_Minh) rồi chạy crond.
set -eu

SCHEDULE="${BACKUP_SCHEDULE:-0 2 * * *}"
KEEP="${BACKUP_KEEP:-7}"

# Giờ của máy chứa tác vụ theo Asia/Ho_Chi_Minh để lịch 2:00 và tên tệp đúng giờ Việt Nam.
ln -sf "/usr/share/zoneinfo/${TZ:-Asia/Ho_Chi_Minh}" /etc/localtime

mkdir -p /backups
chmod 700 /backups

# Mật khẩu cho pg_dump để trong .pgpass (chỉ root đọc được), không ghi vào lịch hay nhật ký.
ESCAPED=$(printf '%s' "$DB_PASSWORD" | sed 's/\\/\\\\/g; s/:/\\:/g')
printf '%s:5432:%s:%s:%s\n' "$DB_HOST" "$DB_NAME" "$DB_USERNAME" "$ESCAPED" > /root/.pgpass
chmod 600 /root/.pgpass

cat > /etc/crontabs/root <<EOF
$SCHEDULE DB_HOST=$DB_HOST DB_NAME=$DB_NAME DB_USERNAME=$DB_USERNAME BACKUP_KEEP=$KEEP /usr/local/bin/backup.sh >> /proc/1/fd/1 2>&1
EOF

echo "Lich sao luu: '$SCHEDULE' theo gio $(date +%Z), giu $KEEP ban gan nhat"
exec crond -f -l 8