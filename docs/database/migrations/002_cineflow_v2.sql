-- =====================================================================
-- CineFlow v2 — Database Migration Script
-- Idempotent schema additions for CineFlow v2 upgrade
-- =====================================================================

USE cineflow_db;

-- 1. Alter bookings table to store pricing calculation snapshot
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'cineflow_db' 
      AND TABLE_NAME = 'bookings' 
      AND COLUMN_NAME = 'pricing_snapshot'
);
SET @sql := IF(@col_exists = 0, 'ALTER TABLE bookings ADD COLUMN pricing_snapshot TEXT NULL AFTER status', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Pricing Rules Table
CREATE TABLE IF NOT EXISTS pricing_rules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    rule_type ENUM('OCCUPANCY', 'TIME_TO_SHOW', 'DAY_TYPE', 'TIME_OF_DAY') NOT NULL,
    min_value DECIMAL(10, 2) NULL,
    max_value DECIMAL(10, 2) NULL,
    adjustment_pct DECIMAL(5, 2) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pricing_rules_active (active),
    INDEX idx_pricing_rules_type (rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Price Quotes Table (for locked pricing through checkout)
CREATE TABLE IF NOT EXISTS price_quotes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    showtime_id INT NOT NULL,
    seat_type ENUM('REGULAR', 'PREMIUM') NOT NULL,
    base_price DECIMAL(10, 2) NOT NULL,
    final_price DECIMAL(10, 2) NOT NULL,
    breakdown_json TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    CONSTRAINT fk_quotes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_quotes_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE CASCADE,
    INDEX idx_quotes_lookup (user_id, showtime_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Waitlist Entries Table
CREATE TABLE IF NOT EXISTS waitlist_entries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    showtime_id INT NOT NULL,
    seats_wanted INT NOT NULL,
    seat_preference ENUM('BEST_OVERALL', 'CENTER', 'FRONT', 'BACK', 'PREMIUM_ONLY') NOT NULL DEFAULT 'BEST_OVERALL',
    status ENUM('WAITING', 'OFFERED', 'CLAIMED', 'EXPIRED', 'CANCELLED', 'DECLINED') NOT NULL DEFAULT 'WAITING',
    position_ts DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    offered_at DATETIME NULL,
    offer_expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_waitlist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_waitlist_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE CASCADE,
    INDEX idx_waitlist_queue (showtime_id, status, position_ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Notifications Table
CREATE TABLE IF NOT EXISTS notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    type ENUM('WAITLIST_OFFER', 'OFFER_EXPIRED', 'BOOKING_CONFIRMED', 'BOOKING_CANCELLED', 'SYSTEM') NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    ref_type VARCHAR(50) NULL,
    ref_id INT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. User Preferences Table (for Personalised Recommendations)
CREATE TABLE IF NOT EXISTS user_preferences (
    user_id INT PRIMARY KEY,
    genres VARCHAR(255) NOT NULL,
    languages VARCHAR(255) NOT NULL,
    min_rating DECIMAL(3, 1) NOT NULL DEFAULT 7.0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Tickets Table (one unique tamper-proof token per reserved seat)
CREATE TABLE IF NOT EXISTS tickets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    booking_seat_id INT NOT NULL,
    showtime_id INT NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    status ENUM('VALID', 'USED', 'CANCELLED') NOT NULL DEFAULT 'VALID',
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME NULL,
    used_by_user_id INT NULL,
    CONSTRAINT fk_tickets_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_tickets_bseat FOREIGN KEY (booking_seat_id) REFERENCES booking_seats(id) ON DELETE CASCADE,
    CONSTRAINT fk_tickets_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_user FOREIGN KEY (used_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_tickets_token (token),
    INDEX idx_tickets_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. Entry Scans Audit Log Table
CREATE TABLE IF NOT EXISTS entry_scans (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NULL,
    token_hash CHAR(64) NOT NULL,
    result ENUM('ADMITTED', 'ALREADY_USED', 'CANCELLED', 'INVALID', 'WRONG_SHOW', 'OUTSIDE_WINDOW') NOT NULL,
    scanned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scanned_by_user_id INT NOT NULL,
    CONSTRAINT fk_scans_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE SET NULL,
    CONSTRAINT fk_scans_user FOREIGN KEY (scanned_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_scans_ticket (ticket_id),
    INDEX idx_scans_time (scanned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. Insert default pricing rules if none exist
INSERT IGNORE INTO pricing_rules (id, name, rule_type, min_value, max_value, adjustment_pct, priority, active, description) VALUES
(1, 'Low Occupancy Discount', 'OCCUPANCY', 0.00, 30.00, -10.00, 10, TRUE, 'Discount when occupancy is below 30%'),
(2, 'Medium Demand Surge', 'OCCUPANCY', 60.00, 80.00, 10.00, 20, TRUE, 'Slight surge when occupancy is between 60% and 80%'),
(3, 'High Demand Surge', 'OCCUPANCY', 80.01, 100.00, 25.00, 30, TRUE, 'Significant surge when occupancy exceeds 80%'),
(4, 'Early Bird Special', 'TIME_TO_SHOW', 168.00, 720.00, -10.00, 40, TRUE, 'Early bird discount for bookings more than 7 days ahead'),
(5, 'Last-Minute Matinee Clearance', 'TIME_TO_SHOW', 0.00, 3.00, -15.00, 50, TRUE, 'Clearance discount within 3 hours when occupancy is low'),
(6, 'Weekend Prime Surge', 'DAY_TYPE', 5.00, 7.00, 15.00, 60, TRUE, 'Friday, Saturday, and Sunday prime day surge'),
(7, 'Weekday Morning/Matinee Discount', 'TIME_OF_DAY', 9.00, 17.00, -10.00, 70, TRUE, 'Weekday before 5:00 PM matinee discount'),
(8, 'Prime Evening Hours', 'TIME_OF_DAY', 18.00, 22.00, 10.00, 80, TRUE, 'Prime evening slot (6:00 PM - 10:00 PM) surge');
