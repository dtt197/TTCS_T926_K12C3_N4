ALTER TABLE bookings
DROP CONSTRAINT IF EXISTS bookings_status_check;

ALTER TABLE bookings
ADD CONSTRAINT bookings_status_check
CHECK (
    status IN (
        'CHO_XAC_NHAN',
        'DA_XAC_NHAN',
        'DA_HUY',
        'DA_NHAN_PHONG',
        'DA_TRA_PHONG',
        'DA_HET_HAN'
    )
);