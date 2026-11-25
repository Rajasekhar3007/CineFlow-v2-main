-- =====================================================================
-- CineFlow v3 Full Suite Migration: Features 3, 4, 5
-- Migration: 004_cineflow_v3_full.sql
-- Idempotent schema additions for:
--  3. Concessions & F&B Pre-Ordering (concessions, booking_concessions)
--  4. Loyalty & Promo Codes (user_loyalty, promo_codes)
--  5. Verified Customer Reviews & Sentiment (movie_reviews)
-- =====================================================================

USE cineflow_db;

-- 1. F&B Concessions Table
CREATE TABLE IF NOT EXISTS concessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category ENUM('SNACK', 'BEVERAGE', 'COMBO', 'DESSERT') NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Booking Concessions Line Items
CREATE TABLE IF NOT EXISTS booking_concessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    concession_id INT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    pickup_status ENUM('PENDING', 'PREPARING', 'READY', 'COLLECTED') NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_bconcession_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_bconcession_item FOREIGN KEY (concession_id) REFERENCES concessions(id) ON DELETE RESTRICT,
    INDEX idx_bconcession_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Loyalty Table
CREATE TABLE IF NOT EXISTS user_loyalty (
    user_id INT PRIMARY KEY,
    points_balance INT NOT NULL DEFAULT 0,
    tier ENUM('SILVER', 'GOLD', 'PLATINUM') NOT NULL DEFAULT 'SILVER',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loyalty_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Promotional Promo Codes Table
CREATE TABLE IF NOT EXISTS promo_codes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    discount_type ENUM('PERCENTAGE', 'FLAT') NOT NULL,
    discount_value DECIMAL(10, 2) NOT NULL,
    min_booking_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    max_discount DECIMAL(10, 2) NULL,
    valid_until DATETIME NOT NULL,
    usage_limit INT NOT NULL DEFAULT 100,
    used_count INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Movie Reviews Table
CREATE TABLE IF NOT EXISTS movie_reviews (
    id INT AUTO_INCREMENT PRIMARY KEY,
    movie_id INT NOT NULL,
    user_id INT NOT NULL,
    booking_id INT NOT NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    review_text TEXT,
    sentiment ENUM('POSITIVE', 'NEUTRAL', 'NEGATIVE') NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rev_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_rev_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_rev_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_movie_review UNIQUE (user_id, movie_id),
    INDEX idx_rev_movie (movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed Concessions Data
INSERT IGNORE INTO concessions (id, name, category, price, description) VALUES
(1, 'Caramel Jumbo Popcorn', 'SNACK', 240.00, 'Freshly popped warm golden caramel popcorn'),
(2, 'Classic Salted Popcorn', 'SNACK', 180.00, 'Crispy buttery movie-theatre classic salted popcorn'),
(3, 'Cheesy Jalapeno Nachos', 'SNACK', 210.00, 'Crisp tortilla chips with warm molten cheddar cheese & salsa'),
(4, 'Cold Coffee Frappe', 'BEVERAGE', 160.00, 'Rich chilled espresso blended with cream and dark chocolate'),
(5, 'Pepsi Black Large (650ml)', 'BEVERAGE', 120.00, 'Zero-sugar ice-cold refreshing cola'),
(6, 'Blockbuster Couple Combo', 'COMBO', 450.00, '1 Large Caramel Popcorn + 2 Pepsi + 1 Cheesy Nachos (15% savings)'),
(7, 'Belgian Choco Lava Cake', 'DESSERT', 140.00, 'Warm molten dark chocolate cake served fresh');

-- Seed Promo Codes Data
INSERT IGNORE INTO promo_codes (id, code, discount_type, discount_value, min_booking_amount, max_discount, valid_until, usage_limit, used_count, active) VALUES
(1, 'CINEFLOW20', 'PERCENTAGE', 20.00, 300.00, 100.00, NOW() + INTERVAL 90 DAY, 500, 24, TRUE),
(2, 'BLOCKBUSTER50', 'FLAT', 50.00, 250.00, 50.00, NOW() + INTERVAL 90 DAY, 200, 15, TRUE),
(3, 'VIP100', 'FLAT', 100.00, 500.00, 100.00, NOW() + INTERVAL 90 DAY, 100, 8, TRUE);

-- Seed Loyalty Accounts for demo users
INSERT IGNORE INTO user_loyalty (user_id, points_balance, tier) VALUES
(2, 340, 'SILVER'),
(3, 850, 'GOLD');
