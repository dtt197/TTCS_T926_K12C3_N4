-- S1-02: tài khoản mới dùng mật khẩu tạm, bắt buộc đổi ở lần đăng nhập đầu tiên
ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;