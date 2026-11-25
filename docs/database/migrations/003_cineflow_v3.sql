-- =====================================================================
-- CineFlow v3 Database Migration: Feature 1 & Feature 2
-- Migration: 003_cineflow_v3.sql
-- Idempotent schema additions for:
--  1. Smart Ticket Rescheduling (reschedule_count, ticket_reschedules audit log)
--  2. Demand Forecasting (demand_forecasts table)
-- =====================================================================

USE cineflow_db;

-- 1. Add reschedule_count column to bookings
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
               WHERE TABLE_SCHEMA = DATABASE() 
                 AND TABLE_NAME = 'bookings' 
                 AND COLUMN_NAME = 'reschedule_count');
SET @sqlstmt := IF(@exist = 0, 'ALTER TABLE bookings ADD COLUMN reschedule_count INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Create ticket_reschedules audit table
CREATE TABLE IF NOT EXISTS ticket_reschedules (
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

-- 3. Create demand_forecasts table
CREATE TABLE IF NOT EXISTS demand_forecasts (
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
