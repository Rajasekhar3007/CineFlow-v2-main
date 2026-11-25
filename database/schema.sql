-- =====================================================================
-- CineFlow - Movie Ticket Reservation System
-- Database Schema Definition (MySQL 8.x, InnoDB)
-- Database Name: cineflow_db
-- =====================================================================

CREATE DATABASE IF NOT EXISTS cineflow_db;
USE cineflow_db;

-- Drop tables in reverse foreign-key dependency order
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS movie_reviews;
DROP TABLE IF EXISTS promo_codes;
DROP TABLE IF EXISTS user_loyalty;
DROP TABLE IF EXISTS booking_concessions;
DROP TABLE IF EXISTS concessions;
DROP TABLE IF EXISTS demand_forecasts;
DROP TABLE IF EXISTS ticket_reschedules;
DROP TABLE IF EXISTS entry_scans;
DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS user_preferences;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS waitlist_entries;
DROP TABLE IF EXISTS price_quotes;
DROP TABLE IF EXISTS pricing_rules;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS seat_holds;
DROP TABLE IF EXISTS booking_seats;
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS showtimes;
DROP TABLE IF EXISTS seats;
DROP TABLE IF EXISTS screens;
DROP TABLE IF EXISTS movies;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Table 1: users
-- Stores system users: ADMIN and CUSTOMER accounts
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(64) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role ENUM('ADMIN', 'CUSTOMER') NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_users_username (username),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 2: movies
-- Stores movie catalog information
-- ---------------------------------------------------------------------
CREATE TABLE movies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    genre VARCHAR(50) NOT NULL,
    language VARCHAR(50) NOT NULL,
    duration_minutes INT NOT NULL,
    rating DECIMAL(3, 1) NOT NULL DEFAULT 0.0,
    description TEXT,
    release_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_movies_title (title),
    INDEX idx_movies_active (active),
    INDEX idx_movies_genre (genre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 3: screens
-- Multiplex auditoriums/screens
-- ---------------------------------------------------------------------
CREATE TABLE screens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    total_rows INT NOT NULL,
    seats_per_row INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 4: seats
-- Physical seat layout per screen
-- ---------------------------------------------------------------------
CREATE TABLE seats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    screen_id INT NOT NULL,
    row_label VARCHAR(5) NOT NULL,
    seat_number INT NOT NULL,
    seat_type ENUM('REGULAR', 'PREMIUM') NOT NULL DEFAULT 'REGULAR',
    CONSTRAINT fk_seats_screen FOREIGN KEY (screen_id) REFERENCES screens(id) ON DELETE CASCADE,
    CONSTRAINT uk_screen_seat UNIQUE (screen_id, row_label, seat_number),
    INDEX idx_seats_screen (screen_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 5: showtimes
-- Scheduled screenings linking movies, screens, and ticket pricing
-- ---------------------------------------------------------------------
CREATE TABLE showtimes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    movie_id INT NOT NULL,
    screen_id INT NOT NULL,
    start_time DATETIME NOT NULL,
    price_regular DECIMAL(10, 2) NOT NULL,
    price_premium DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_showtimes_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_showtimes_screen FOREIGN KEY (screen_id) REFERENCES screens(id) ON DELETE RESTRICT,
    INDEX idx_showtimes_start_time (start_time),
    INDEX idx_showtimes_movie (movie_id),
    INDEX idx_showtimes_screen (screen_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 6: bookings
-- Ticket reservations made by registered customers
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    showtime_id INT NOT NULL,
    booking_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount DECIMAL(10, 2) NOT NULL,
    status ENUM('CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'CONFIRMED',
    pricing_snapshot TEXT NULL,
    reschedule_count INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE RESTRICT,
    INDEX idx_bookings_booking_time (booking_time),
    INDEX idx_bookings_user (user_id),
    INDEX idx_bookings_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 7: booking_seats
-- Line items connecting booked seats to a booking and showtime.
-- The unique key applies only to unreleased seats, preserving cancellation history.
-- ---------------------------------------------------------------------
CREATE TABLE booking_seats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    showtime_id INT NOT NULL,
    seat_id INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    released_at DATETIME NULL,
    active_seat_id INT GENERATED ALWAYS AS (IF(released_at IS NULL, seat_id, NULL)) STORED,
    CONSTRAINT fk_bseats_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_bseats_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bseats_seat FOREIGN KEY (seat_id) REFERENCES seats(id) ON DELETE RESTRICT,
    CONSTRAINT uk_booking_showtime_seat UNIQUE (showtime_id, active_seat_id),
    INDEX idx_bseats_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 8: seat_holds
-- Temporary 5-minute reservations during payment checkout.
-- The UNIQUE (showtime_id, seat_id) constraint prevents duplicate active holds.
-- ---------------------------------------------------------------------
CREATE TABLE seat_holds (
    id INT AUTO_INCREMENT PRIMARY KEY,
    showtime_id INT NOT NULL,
    seat_id INT NOT NULL,
    user_id INT NOT NULL,
    held_until DATETIME NOT NULL,
    CONSTRAINT fk_holds_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE CASCADE,
    CONSTRAINT fk_holds_seat FOREIGN KEY (seat_id) REFERENCES seats(id) ON DELETE CASCADE,
    CONSTRAINT fk_holds_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_hold_showtime_seat UNIQUE (showtime_id, seat_id),
    INDEX idx_holds_held_until (held_until),
    INDEX idx_holds_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 9: payments
-- Simulated payment records for completed bookings
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    method ENUM('CASH', 'UPI', 'CARD', 'RAZORPAY') NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    transaction_id VARCHAR(100) NULL,
    order_id VARCHAR(100) NULL,
    status ENUM('PAID', 'REFUNDED', 'REFUND_PENDING', 'REFUND_FAILED') NOT NULL DEFAULT 'PAID',
    gateway_refund_id VARCHAR(100) NULL,
    refund_next_attempt_at DATETIME NULL,
    paid_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE RESTRICT,
    INDEX idx_payments_status (status),
    INDEX idx_payments_refund_due (status, refund_next_attempt_at),
    INDEX idx_payments_paid_at (paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 10: pricing_rules (Dynamic Pricing Engine)
-- ---------------------------------------------------------------------
CREATE TABLE pricing_rules (
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

-- ---------------------------------------------------------------------
-- Table 11: price_quotes (Time-locked quotes during checkout)
-- ---------------------------------------------------------------------
CREATE TABLE price_quotes (
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

-- ---------------------------------------------------------------------
-- Table 12: waitlist_entries (Automated seat queues)
-- ---------------------------------------------------------------------
CREATE TABLE waitlist_entries (
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

-- ---------------------------------------------------------------------
-- Table 13: notifications (In-app notifications)
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
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

-- ---------------------------------------------------------------------
-- Table 14: user_preferences (Recommendation profiles)
-- ---------------------------------------------------------------------
CREATE TABLE user_preferences (
    user_id INT PRIMARY KEY,
    genres VARCHAR(255) NOT NULL,
    languages VARCHAR(255) NOT NULL,
    min_rating DECIMAL(3, 1) NOT NULL DEFAULT 7.0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 15: tickets (Cryptographic digital passes per seat)
-- ---------------------------------------------------------------------
CREATE TABLE tickets (
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

-- ---------------------------------------------------------------------
-- Table 16: entry_scans (Audit log for QR entry validations)
-- ---------------------------------------------------------------------
CREATE TABLE entry_scans (
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

-- ---------------------------------------------------------------------
-- Table 17: ticket_reschedules (Smart Ticket Rescheduling Audit Log)
-- ---------------------------------------------------------------------
CREATE TABLE ticket_reschedules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    old_showtime_id INT NOT NULL,
    new_showtime_id INT NOT NULL,
    old_seats VARCHAR(150) NOT NULL,
    new_seats VARCHAR(150) NOT NULL,
    fare_difference DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    rescheduled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resched_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    INDEX idx_resched_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 18: demand_forecasts (Demand Forecasting Engine Predictions)
-- ---------------------------------------------------------------------
CREATE TABLE demand_forecasts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    showtime_id INT NOT NULL,
    predicted_occupancy DECIMAL(5, 2) NOT NULL,
    expected_bookings INT NOT NULL,
    demand_category ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL,
    confidence_pct DECIMAL(5, 2) NOT NULL,
    explanation TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_forecast_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes(id) ON DELETE CASCADE,
    INDEX idx_forecast_showtime (showtime_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 19: concessions (F&B Concession Items & Combos)
-- ---------------------------------------------------------------------
CREATE TABLE concessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category ENUM('SNACK', 'BEVERAGE', 'COMBO', 'DESSERT') NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 20: booking_concessions (F&B Orders linked to Bookings)
-- ---------------------------------------------------------------------
CREATE TABLE booking_concessions (
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

-- ---------------------------------------------------------------------
-- Table 21: user_loyalty (Customer Loyalty Points & Membership Tier)
-- ---------------------------------------------------------------------
CREATE TABLE user_loyalty (
    user_id INT PRIMARY KEY,
    points_balance INT NOT NULL DEFAULT 0,
    tier ENUM('SILVER', 'GOLD', 'PLATINUM') NOT NULL DEFAULT 'SILVER',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loyalty_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Table 22: promo_codes (Promotional Discount Coupons)
-- ---------------------------------------------------------------------
CREATE TABLE promo_codes (
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

-- ---------------------------------------------------------------------
-- Table 23: movie_reviews (Verified Customer Reviews & Sentiment)
-- ---------------------------------------------------------------------
CREATE TABLE movie_reviews (
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


