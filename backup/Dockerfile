# S4-10 Lát 3: dịch vụ sao lưu dùng chung image với cơ sở dữ liệu (có sẵn pg_dump, crond, múi giờ)
FROM postgres:15-alpine
COPY backup.sh entrypoint.sh /usr/local/bin/
# Bỏ ký tự xuống dòng kiểu Windows (CRLF) để script chạy được trên Linux
RUN sed -i 's/\r$//' /usr/local/bin/backup.sh /usr/local/bin/entrypoint.sh \
    && chmod 755 /usr/local/bin/backup.sh /usr/local/bin/entrypoint.sh
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]