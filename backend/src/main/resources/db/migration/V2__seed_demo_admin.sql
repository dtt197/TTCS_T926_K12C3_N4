INSERT INTO users (full_name, email, password_hash, role_id, is_active)
SELECT
    'Demo Administrator',
    'admin.demo@homestay.local',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    id,
    TRUE
FROM roles
WHERE code = 'ADMIN';