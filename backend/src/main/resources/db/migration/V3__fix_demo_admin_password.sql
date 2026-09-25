UPDATE users
SET password_hash = '$2a$10$i8R8MorFFrpX4sP1O6xihOX1M3V5ICzfLPuym.EpIZnx7s6y.mwcm',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'admin.demo@homestay.local';
