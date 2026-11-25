USE cineflow_db;

-- Preserve historical seat labels while allowing released seats to be booked again.
ALTER TABLE booking_seats
    ADD COLUMN released_at DATETIME NULL,
    ADD COLUMN active_seat_id INT GENERATED ALWAYS AS (IF(released_at IS NULL, seat_id, NULL)) STORED,
    ADD UNIQUE KEY uk_booking_active_showtime_seat (showtime_id, active_seat_id);

ALTER TABLE booking_seats DROP INDEX uk_booking_showtime_seat;
ALTER TABLE booking_seats RENAME INDEX uk_booking_active_showtime_seat TO uk_booking_showtime_seat;

UPDATE booking_seats bs
JOIN bookings b ON b.id = bs.booking_id
SET bs.released_at = NOW()
WHERE b.status = 'CANCELLED' AND bs.released_at IS NULL;

ALTER TABLE payments
    MODIFY COLUMN status ENUM('PAID', 'REFUNDED', 'REFUND_PENDING', 'REFUND_FAILED') NOT NULL DEFAULT 'PAID',
    ADD COLUMN gateway_refund_id VARCHAR(100) NULL,
    ADD COLUMN refund_next_attempt_at DATETIME NULL,
    ADD INDEX idx_payments_refund_due (status, refund_next_attempt_at);
