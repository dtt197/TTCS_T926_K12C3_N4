CREATE TABLE IF NOT EXISTS room_types (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    standard_capacity INT NOT NULL,
    max_capacity INT NOT NULL,
    number_of_beds INT NOT NULL,
    description VARCHAR(500),
    status BOOLEAN DEFAULT TRUE
);