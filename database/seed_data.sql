-- =====================================================================
-- CineFlow - Movie Ticket Reservation System
-- Deterministic Seed Data (MySQL 8.x)
-- =====================================================================

USE cineflow_db;

-- Clear any existing records in reverse dependency order
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE movie_reviews;
TRUNCATE TABLE user_loyalty;
TRUNCATE TABLE promo_codes;
TRUNCATE TABLE booking_concessions;
TRUNCATE TABLE concessions;
TRUNCATE TABLE demand_forecasts;
TRUNCATE TABLE ticket_reschedules;
TRUNCATE TABLE entry_scans;
TRUNCATE TABLE tickets;
TRUNCATE TABLE notifications;
TRUNCATE TABLE waitlist_entries;
TRUNCATE TABLE user_preferences;
TRUNCATE TABLE price_quotes;
TRUNCATE TABLE pricing_rules;
TRUNCATE TABLE payments;
TRUNCATE TABLE seat_holds;
TRUNCATE TABLE booking_seats;
TRUNCATE TABLE bookings;
TRUNCATE TABLE showtimes;
TRUNCATE TABLE seats;
TRUNCATE TABLE screens;
TRUNCATE TABLE movies;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 1. USERS
-- Password for admin: Admin@123
-- Password for john_doe: Customer@123
-- Password for jane_smith: Customer@123
-- Hashed via SHA-256(salt + password)
-- ---------------------------------------------------------------------
INSERT INTO users (id, username, password_hash, salt, full_name, email, role, active) VALUES
(1, 'admin', '8c8746be97b5fda756b26fb956ac8f714ffe6b9781563d9c65320acc221a8e7a', 'a1b2c3d4e5f60718293a4b5c6d7e8f90', 'System Administrator', 'admin@cineflow.com', 'ADMIN', TRUE),
(2, 'john_doe', 'fec9fdc9ec906d4043e6e24c5ca83d3729eaef3caf2bab0dd75c3f6eb7a030d2', 'b2c3d4e5f6a10718293a4b5c6d7e8f91', 'John Doe', 'john.doe@example.com', 'CUSTOMER', TRUE),
(3, 'jane_smith', '4a85f3ef3e128f29761438937e331cb78887cafcf176de927646623087521c05', 'c3d4e5f6a1b20718293a4b5c6d7e8f92', 'Jane Smith', 'jane.smith@example.com', 'CUSTOMER', TRUE);

-- ---------------------------------------------------------------------
-- 2. SCREENS
-- Screen 1: IMAX (5 rows x 8 seats = 40 seats)
-- Screen 2: Dolby Cinema (4 rows x 6 seats = 24 seats)
-- ---------------------------------------------------------------------
INSERT INTO screens (id, name, total_rows, seats_per_row) VALUES
(1, 'Audi 1 - IMAX', 5, 8),
(2, 'Audi 2 - Dolby Cinema', 4, 6);

-- ---------------------------------------------------------------------
-- 3. SEATS (Screen 1: Rows A-C Regular, D-E Premium; Screen 2: A-B Regular, C-D Premium)
-- ---------------------------------------------------------------------
-- Screen 1: Rows A to E, 8 seats each (IDs 1 to 40)
INSERT INTO seats (screen_id, row_label, seat_number, seat_type) VALUES
-- Row A (Regular)
(1, 'A', 1, 'REGULAR'), (1, 'A', 2, 'REGULAR'), (1, 'A', 3, 'REGULAR'), (1, 'A', 4, 'REGULAR'),
(1, 'A', 5, 'REGULAR'), (1, 'A', 6, 'REGULAR'), (1, 'A', 7, 'REGULAR'), (1, 'A', 8, 'REGULAR'),
-- Row B (Regular)
(1, 'B', 1, 'REGULAR'), (1, 'B', 2, 'REGULAR'), (1, 'B', 3, 'REGULAR'), (1, 'B', 4, 'REGULAR'),
(1, 'B', 5, 'REGULAR'), (1, 'B', 6, 'REGULAR'), (1, 'B', 7, 'REGULAR'), (1, 'B', 8, 'REGULAR'),
-- Row C (Regular)
(1, 'C', 1, 'REGULAR'), (1, 'C', 2, 'REGULAR'), (1, 'C', 3, 'REGULAR'), (1, 'C', 4, 'REGULAR'),
(1, 'C', 5, 'REGULAR'), (1, 'C', 6, 'REGULAR'), (1, 'C', 7, 'REGULAR'), (1, 'C', 8, 'REGULAR'),
-- Row D (Premium)
(1, 'D', 1, 'PREMIUM'), (1, 'D', 2, 'PREMIUM'), (1, 'D', 3, 'PREMIUM'), (1, 'D', 4, 'PREMIUM'),
(1, 'D', 5, 'PREMIUM'), (1, 'D', 6, 'PREMIUM'), (1, 'D', 7, 'PREMIUM'), (1, 'D', 8, 'PREMIUM'),
-- Row E (Premium)
(1, 'E', 1, 'PREMIUM'), (1, 'E', 2, 'PREMIUM'), (1, 'E', 3, 'PREMIUM'), (1, 'E', 4, 'PREMIUM'),
(1, 'E', 5, 'PREMIUM'), (1, 'E', 6, 'PREMIUM'), (1, 'E', 7, 'PREMIUM'), (1, 'E', 8, 'PREMIUM');

-- Screen 2: Rows A to D, 6 seats each (IDs 41 to 64)
INSERT INTO seats (screen_id, row_label, seat_number, seat_type) VALUES
-- Row A (Regular)
(2, 'A', 1, 'REGULAR'), (2, 'A', 2, 'REGULAR'), (2, 'A', 3, 'REGULAR'), (2, 'A', 4, 'REGULAR'), (2, 'A', 5, 'REGULAR'), (2, 'A', 6, 'REGULAR'),
-- Row B (Regular)
(2, 'B', 1, 'REGULAR'), (2, 'B', 2, 'REGULAR'), (2, 'B', 3, 'REGULAR'), (2, 'B', 4, 'REGULAR'), (2, 'B', 5, 'REGULAR'), (2, 'B', 6, 'REGULAR'),
-- Row C (Premium)
(2, 'C', 1, 'PREMIUM'), (2, 'C', 2, 'PREMIUM'), (2, 'C', 3, 'PREMIUM'), (2, 'C', 4, 'PREMIUM'), (2, 'C', 5, 'PREMIUM'), (2, 'C', 6, 'PREMIUM'),
-- Row D (Premium)
(2, 'D', 1, 'PREMIUM'), (2, 'D', 2, 'PREMIUM'), (2, 'D', 3, 'PREMIUM'), (2, 'D', 4, 'PREMIUM'), (2, 'D', 5, 'PREMIUM'), (2, 'D', 6, 'PREMIUM');

-- ---------------------------------------------------------------------
-- 4. MOVIES (8 Featured Movies)
-- ---------------------------------------------------------------------
INSERT INTO movies (id, title, genre, language, duration_minutes, rating, description, release_date, active) VALUES
(1, 'Inception', 'Sci-Fi', 'English', 148, 8.8, 'A thief who steals corporate secrets through dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.', '2010-07-16', TRUE),
(2, 'Interstellar', 'Sci-Fi', 'English', 169, 8.7, 'When Earth becomes uninhabitable, a team of researchers and astronauts travels through a wormhole in search of a new home for humanity.', '2014-11-07', TRUE),
(3, 'The Dark Knight', 'Action', 'English', 152, 9.0, 'Batman faces the ultimate psychological and physical test against the Joker, a criminal mastermind unleashing chaos on Gotham City.', '2008-07-18', TRUE),
(4, 'Oppenheimer', 'Drama', 'English', 180, 8.9, 'The story of J. Robert Oppenheimer and his pivotal role in developing the atomic bomb during the Manhattan Project.', '2023-07-21', TRUE),
(5, 'Dune: Part Two', 'Sci-Fi', 'English', 166, 8.6, 'Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.', '2024-03-01', TRUE),
(6, 'RRR', 'Action', 'Telugu', 187, 8.0, 'Two legendary Indian revolutionaries fight against British colonialists in the 1920s in this epic action drama.', '2022-03-25', TRUE),
(7, 'Kantara', 'Action', 'Kannada', 148, 8.2, 'When greed paves the way for betrayal, a Kambala champion embarks on a mystic journey to protect his tribal lands.', '2022-09-30', TRUE),
(8, 'Spirited Away', 'Animation', 'Japanese', 125, 8.6, 'A ten-year-old girl wanders into a world ruled by gods, witches, and spirits, where humans are changed into beasts.', '2001-07-20', TRUE);

-- ---------------------------------------------------------------------
-- 5. SHOWTIMES (4 Historical for reports + 8 Upcoming over the next 5 days)
-- ---------------------------------------------------------------------
-- Historical Showtimes (IDs 1-4)
INSERT INTO showtimes (id, movie_id, screen_id, start_time, price_regular, price_premium) VALUES
(1, 1, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 250.00, 350.00),
(2, 3, 2, DATE_SUB(NOW(), INTERVAL 4 DAY), 220.00, 320.00),
(3, 4, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 280.00, 400.00),
(4, 6, 2, DATE_SUB(NOW(), INTERVAL 1 DAY), 200.00, 300.00);

-- Upcoming Showtimes (IDs 5-12)
INSERT INTO showtimes (id, movie_id, screen_id, start_time, price_regular, price_premium) VALUES
(5, 5, 1, DATE_ADD(DATE_ADD(CURRENT_DATE(), INTERVAL 1 DAY), INTERVAL 10 HOUR), 250.00, 350.00),
(6, 1, 1, DATE_ADD(DATE_ADD(CURRENT_DATE(), INTERVAL 1 DAY), INTERVAL 14 HOUR), 250.00, 350.00),
(7, 4, 1, DATE_ADD(DATE_ADD(CURRENT_DATE(), INTERVAL 1 DAY), INTERVAL 19 HOUR), 280.00, 400.00),
(8, 2, 2, DATE_ADD(DATE_ADD(CURRENT_DATE(), INTERVAL 2 DAY), INTERVAL 11 HOUR), 240.00, 340.00),
(9, 3, 2, DATE_ADD(DATE_ADD(CURRENT_DATE(), INTERVAL 2 DAY), INTERVAL 16 HOUR), 220.00, 320.00),
(10, 6, 1, DATE_ADD(DATE_ADD(CURRENT_DATE(), INTERVAL 3 DAY), INTERVAL 13 HOUR), 200.00, 300.00),
(11, 7, 2, DATE_ADD(DATE_ADD(CURRENT_DATE(), INTERVAL 4 DAY), INTERVAL 17 HOUR), 200.00, 280.00),
(12, 8, 1, DATE_ADD(DATE_ADD(CURRENT_DATE(), INTERVAL 5 DAY), INTERVAL 19 HOUR), 180.00, 260.00);

-- ---------------------------------------------------------------------
-- 6. HISTORICAL BOOKINGS (25 Bookings across Historical Showtimes 1-4)
-- ---------------------------------------------------------------------
-- Showtime 1 (Inception, Screen 1): 7 bookings
INSERT INTO bookings (id, user_id, showtime_id, booking_time, total_amount, status) VALUES
(1,  2, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), 250.00, 'CONFIRMED'),
(2,  3, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), 350.00, 'CONFIRMED'),
(3,  2, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), 500.00, 'CONFIRMED'),
(4,  3, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 700.00, 'CONFIRMED'),
(5,  2, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 250.00, 'CONFIRMED'),
(6,  3, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 350.00, 'CONFIRMED'),
(7,  2, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 250.00, 'CANCELLED');

-- Showtime 2 (The Dark Knight, Screen 2): 6 bookings
INSERT INTO bookings (id, user_id, showtime_id, booking_time, total_amount, status) VALUES
(8,  3, 2, DATE_SUB(NOW(), INTERVAL 5 DAY), 220.00, 'CONFIRMED'),
(9,  2, 2, DATE_SUB(NOW(), INTERVAL 5 DAY), 320.00, 'CONFIRMED'),
(10, 3, 2, DATE_SUB(NOW(), INTERVAL 4 DAY), 440.00, 'CONFIRMED'),
(11, 2, 2, DATE_SUB(NOW(), INTERVAL 4 DAY), 640.00, 'CONFIRMED'),
(12, 3, 2, DATE_SUB(NOW(), INTERVAL 4 DAY), 220.00, 'CONFIRMED'),
(13, 2, 2, DATE_SUB(NOW(), INTERVAL 4 DAY), 320.00, 'CANCELLED');

-- Showtime 3 (Oppenheimer, Screen 1): 7 bookings
INSERT INTO bookings (id, user_id, showtime_id, booking_time, total_amount, status) VALUES
(14, 2, 3, DATE_SUB(NOW(), INTERVAL 3 DAY), 280.00, 'CONFIRMED'),
(15, 3, 3, DATE_SUB(NOW(), INTERVAL 3 DAY), 400.00, 'CONFIRMED'),
(16, 2, 3, DATE_SUB(NOW(), INTERVAL 3 DAY), 560.00, 'CONFIRMED'),
(17, 3, 3, DATE_SUB(NOW(), INTERVAL 2 DAY), 800.00, 'CONFIRMED'),
(18, 2, 3, DATE_SUB(NOW(), INTERVAL 2 DAY), 280.00, 'CONFIRMED'),
(19, 3, 3, DATE_SUB(NOW(), INTERVAL 2 DAY), 400.00, 'CONFIRMED'),
(20, 2, 3, DATE_SUB(NOW(), INTERVAL 2 DAY), 560.00, 'CONFIRMED');

-- Showtime 4 (RRR, Screen 2): 5 bookings
INSERT INTO bookings (id, user_id, showtime_id, booking_time, total_amount, status) VALUES
(21, 3, 4, DATE_SUB(NOW(), INTERVAL 2 DAY), 200.00, 'CONFIRMED'),
(22, 2, 4, DATE_SUB(NOW(), INTERVAL 2 DAY), 300.00, 'CONFIRMED'),
(23, 3, 4, DATE_SUB(NOW(), INTERVAL 1 DAY), 400.00, 'CONFIRMED'),
(24, 2, 4, DATE_SUB(NOW(), INTERVAL 1 DAY), 600.00, 'CONFIRMED'),
(25, 3, 4, DATE_SUB(NOW(), INTERVAL 1 DAY), 200.00, 'CONFIRMED');

-- ---------------------------------------------------------------------
-- 7. BOOKING SEATS (Connects Seats to Bookings)
-- Note: Screen 1 seat IDs: 1-40. Screen 2 seat IDs: 41-64.
-- ---------------------------------------------------------------------
-- Showtime 1 (Screen 1):
INSERT INTO booking_seats (booking_id, showtime_id, seat_id, price) VALUES
(1, 1, 1, 250.00),                               -- Seat A1 (Regular)
(2, 1, 25, 350.00),                              -- Seat D1 (Premium)
(3, 1, 2, 250.00), (3, 1, 3, 250.00),            -- Seats A2, A3 (Regular)
(4, 1, 26, 350.00), (4, 1, 27, 350.00),          -- Seats D2, D3 (Premium)
(5, 1, 4, 250.00),                               -- Seat A4 (Regular)
(6, 1, 28, 350.00),                              -- Seat D4 (Premium)
(7, 1, 5, 250.00);                               -- Seat A5 (Cancelled booking)

-- Showtime 2 (Screen 2):
INSERT INTO booking_seats (booking_id, showtime_id, seat_id, price) VALUES
(8,  2, 41, 220.00),                             -- Seat A1 (Regular)
(9,  2, 53, 320.00),                             -- Seat C1 (Premium)
(10, 2, 42, 220.00), (10, 2, 43, 220.00),         -- Seats A2, A3 (Regular)
(11, 2, 54, 320.00), (11, 2, 55, 320.00),         -- Seats C2, C3 (Premium)
(12, 2, 44, 220.00),                             -- Seat A4 (Regular)
(13, 2, 56, 320.00);                             -- Seat C4 (Cancelled booking)

-- Showtime 3 (Screen 1):
INSERT INTO booking_seats (booking_id, showtime_id, seat_id, price) VALUES
(14, 3, 9, 280.00),                              -- Seat B1 (Regular)
(15, 3, 33, 400.00),                             -- Seat E1 (Premium)
(16, 3, 10, 280.00), (16, 3, 11, 280.00),        -- Seats B2, B3 (Regular)
(17, 3, 34, 400.00), (17, 3, 35, 400.00),        -- Seats E2, E3 (Premium)
(18, 3, 12, 280.00),                             -- Seat B4 (Regular)
(19, 3, 36, 400.00),                             -- Seat E4 (Premium)
(20, 3, 13, 280.00), (20, 3, 14, 280.00);        -- Seats B5, B6 (Regular)

-- Showtime 4 (Screen 2):
INSERT INTO booking_seats (booking_id, showtime_id, seat_id, price) VALUES
(21, 4, 47, 200.00),                             -- Seat B1 (Regular)
(22, 4, 59, 300.00),                             -- Seat D1 (Premium)
(23, 4, 48, 200.00), (23, 4, 49, 200.00),        -- Seats B2, B3 (Regular)
(24, 4, 60, 300.00), (24, 4, 61, 300.00),        -- Seats D2, D3 (Premium)
(25, 4, 50, 200.00);                             -- Seat B4 (Regular)

-- ---------------------------------------------------------------------
-- 8. PAYMENTS (Paid & Refunded Payments for Historical Bookings)
-- ---------------------------------------------------------------------
INSERT INTO payments (id, booking_id, method, amount, status, paid_at) VALUES
(1,  1,  'CARD', 250.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 6 DAY)),
(2,  2,  'UPI',  350.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 6 DAY)),
(3,  3,  'CARD', 500.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 6 DAY)),
(4,  4,  'UPI',  700.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 5 DAY)),
(5,  5,  'CASH', 250.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 5 DAY)),
(6,  6,  'CARD', 350.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 5 DAY)),
(7,  7,  'UPI',  250.00, 'REFUNDED', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(8,  8,  'CARD', 220.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 5 DAY)),
(9,  9,  'UPI',  320.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 5 DAY)),
(10, 10, 'CASH', 440.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 4 DAY)),
(11, 11, 'CARD', 640.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 4 DAY)),
(12, 12, 'UPI',  220.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 4 DAY)),
(13, 13, 'CARD', 320.00, 'REFUNDED', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(14, 14, 'UPI',  280.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 3 DAY)),
(15, 15, 'CARD', 400.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 3 DAY)),
(16, 16, 'CASH', 560.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 3 DAY)),
(17, 17, 'UPI',  800.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 2 DAY)),
(18, 18, 'CARD', 280.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 2 DAY)),
(19, 19, 'UPI',  400.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 2 DAY)),
(20, 20, 'CARD', 560.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 2 DAY)),
(21, 21, 'CASH', 200.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 2 DAY)),
(22, 22, 'UPI',  300.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 2 DAY)),
(23, 23, 'CARD', 400.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 1 DAY)),
(24, 24, 'UPI',  600.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 1 DAY)),
(25, 25, 'CARD', 200.00, 'PAID',     DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ---------------------------------------------------------------------
-- 9. DEFAULT PRICING RULES (CineFlow v2 Dynamic Pricing Engine)
-- ---------------------------------------------------------------------
INSERT INTO pricing_rules (id, name, rule_type, min_value, max_value, adjustment_pct, priority, active, description) VALUES
(1, 'Low Occupancy Discount', 'OCCUPANCY', 0.00, 30.00, -10.00, 10, TRUE, 'Discount when occupancy is below 30%'),
(2, 'Medium Demand Surge', 'OCCUPANCY', 60.00, 80.00, 10.00, 20, TRUE, 'Slight surge when occupancy is between 60% and 80%'),
(3, 'High Demand Surge', 'OCCUPANCY', 80.01, 100.00, 25.00, 30, TRUE, 'Significant surge when occupancy exceeds 80%'),
(4, 'Early Bird Special', 'TIME_TO_SHOW', 168.00, 720.00, -10.00, 40, TRUE, 'Early bird discount for bookings more than 7 days ahead'),
(5, 'Last-Minute Matinee Clearance', 'TIME_TO_SHOW', 0.00, 3.00, -15.00, 50, TRUE, 'Clearance discount within 3 hours when occupancy is low'),
(6, 'Weekend Prime Surge', 'DAY_TYPE', 5.00, 7.00, 15.00, 60, TRUE, 'Friday, Saturday, and Sunday prime day surge'),
(7, 'Weekday Morning/Matinee Discount', 'TIME_OF_DAY', 9.00, 17.00, -10.00, 70, TRUE, 'Weekday before 5:00 PM matinee discount'),
(8, 'Prime Evening Hours', 'TIME_OF_DAY', 18.00, 22.00, 10.00, 80, TRUE, 'Prime evening slot (6:00 PM - 10:00 PM) surge');

-- ---------------------------------------------------------------------
-- 10. USER TASTE PREFERENCES (Personalised Recommendation Engine)
-- ---------------------------------------------------------------------
INSERT INTO user_preferences (user_id, genres, languages, min_rating) VALUES
(2, 'Sci-Fi,Action', 'English', 8.0),
(3, 'Drama,Thriller', 'Hindi,English', 7.5)
ON DUPLICATE KEY UPDATE genres=VALUES(genres), languages=VALUES(languages), min_rating=VALUES(min_rating);

-- ---------------------------------------------------------------------
-- 11. WAITLIST ENTRIES & NOTIFICATIONS (Waitlist System)
-- ---------------------------------------------------------------------
INSERT INTO waitlist_entries (id, user_id, showtime_id, seats_wanted, seat_preference, status, position_ts) VALUES
(1, 3, 5, 2, 'CENTER', 'WAITING', NOW())
ON DUPLICATE KEY UPDATE status=VALUES(status);

INSERT INTO notifications (id, user_id, type, title, message, ref_type, ref_id, is_read, created_at) VALUES
(1, 2, 'SYSTEM', 'Welcome to CineFlow v2', 'Experience our new personalised recommendations, dynamic pricing, and digital QR gate pass!', NULL, NULL, TRUE, NOW()),
(2, 3, 'WAITLIST_OFFER', '🎟 Seats Available for You!', 'Great news! 2 seats have opened up for Dune: Part Two. Click to claim your booking before the offer expires!', 'WAITLIST', 1, FALSE, NOW())
ON DUPLICATE KEY UPDATE title=VALUES(title);

-- ---------------------------------------------------------------------
-- 12. SAMPLE DIGITAL TICKETS & ENTRY GATE AUDIT SCANS
-- ---------------------------------------------------------------------
INSERT INTO tickets (id, booking_id, booking_seat_id, showtime_id, token, status, issued_at) VALUES
(1, 1, 1, 1, 'CF1.1.dGVzdE5vbmNlMTIzNA.dGVzdFNpZ25hdHVyZTE2', 'USED', DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(2, 2, 2, 1, 'CF1.2.c2FtcGxlTm9uY2UyMzQ1.c2FtcGxlU2lnbmF0dXJlMTY', 'VALID', NOW())
ON DUPLICATE KEY UPDATE status=VALUES(status);

INSERT INTO entry_scans (ticket_id, token_hash, result, scanned_at, scanned_by_user_id) VALUES
(1, '8c8746be97b5fda756b26fb956ac8f714ffe6b9781563d9c65320acc221a8e7a', 'ADMITTED', DATE_SUB(NOW(), INTERVAL 1 HOUR), 1);

-- ---------------------------------------------------------------------
-- 13. F&B CONCESSIONS (CineFlow v3 Concessions Pre-Ordering)
-- ---------------------------------------------------------------------
INSERT INTO concessions (id, name, category, price, description, active) VALUES
(1, 'Caramel Jumbo Popcorn', 'SNACK', 240.00, 'Freshly popped warm golden caramel popcorn', TRUE),
(2, 'Classic Salted Popcorn', 'SNACK', 180.00, 'Crispy buttery movie-theatre classic salted popcorn', TRUE),
(3, 'Cheesy Jalapeno Nachos', 'SNACK', 210.00, 'Crisp tortilla chips with warm molten cheddar cheese & salsa', TRUE),
(4, 'Cold Coffee Frappe', 'BEVERAGE', 160.00, 'Rich chilled espresso blended with cream and dark chocolate', TRUE),
(5, 'Pepsi Black Large (650ml)', 'BEVERAGE', 120.00, 'Zero-sugar ice-cold refreshing cola', TRUE),
(6, 'Blockbuster Couple Combo', 'COMBO', 450.00, '1 Large Caramel Popcorn + 2 Pepsi + 1 Cheesy Nachos (15% savings)', TRUE),
(7, 'Belgian Choco Lava Cake', 'DESSERT', 140.00, 'Warm molten dark chocolate cake served fresh', TRUE)
ON DUPLICATE KEY UPDATE name=VALUES(name), price=VALUES(price), description=VALUES(description);

-- ---------------------------------------------------------------------
-- 14. PROMO CODES (CineFlow v3 Promotional Discount Engine)
-- ---------------------------------------------------------------------
INSERT INTO promo_codes (id, code, discount_type, discount_value, min_booking_amount, max_discount, valid_until, usage_limit, used_count, active) VALUES
(1, 'CINEFLOW20', 'PERCENTAGE', 20.00, 300.00, 100.00, NOW() + INTERVAL 90 DAY, 500, 24, TRUE),
(2, 'BLOCKBUSTER50', 'FLAT', 50.00, 250.00, 50.00, NOW() + INTERVAL 90 DAY, 200, 15, TRUE),
(3, 'VIP100', 'FLAT', 100.00, 500.00, 100.00, NOW() + INTERVAL 90 DAY, 100, 8, TRUE)
ON DUPLICATE KEY UPDATE discount_value=VALUES(discount_value), active=VALUES(active);

-- ---------------------------------------------------------------------
-- 15. USER LOYALTY REWARDS (CineFlow v3 Customer Loyalty)
-- ---------------------------------------------------------------------
INSERT INTO user_loyalty (user_id, points_balance, tier, updated_at) VALUES
(2, 340, 'SILVER', NOW()),
(3, 850, 'GOLD', NOW())
ON DUPLICATE KEY UPDATE points_balance=VALUES(points_balance), tier=VALUES(tier);

-- ---------------------------------------------------------------------
-- 16. VERIFIED MOVIE REVIEWS (CineFlow v3 Verified Reviews & Sentiment)
-- ---------------------------------------------------------------------
INSERT INTO movie_reviews (id, movie_id, user_id, booking_id, rating, review_text, sentiment, created_at) VALUES
(1, 1, 2, 1, 5, 'Absolutely mindblowing masterpiece! Christopher Nolan and Cillian Murphy deliver sheer cinematic excellence. The visual and sound design are unbelievable!', 'POSITIVE', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 2, 3, 2, 4, 'Visually breathtaking! The desert world-building and Hans Zimmer score were phenomenal, though the pacing felt a bit deliberate in the middle act.', 'POSITIVE', DATE_SUB(NOW(), INTERVAL 1 DAY))
ON DUPLICATE KEY UPDATE rating=VALUES(rating), review_text=VALUES(review_text), sentiment=VALUES(sentiment);




