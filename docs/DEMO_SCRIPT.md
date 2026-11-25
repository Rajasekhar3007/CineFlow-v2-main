# CineFlow v2 — University Viva & Live Demo Walkthrough Script

> **Target Audience:** University Faculty & Project Examiners  
> **Mission:** Demonstrate 5 technically deep, robust features and modern glassmorphic animated UX in under 60 seconds each, with bulletproof theoretical defense.

---

## 0. Quick Setup & Demo Credentials

| Role | Username | Password | Notes |
|---|---|---|---|
| **System Administrator** | `admin` | `Admin@123` | Entry Gate scanner, Dynamic Pricing simulator, Waitlist monitor |
| **Customer 1** | `john_doe` | `Customer@123` | Active taste profile: Sci-Fi & Action |
| **Customer 2** | `jane_smith` | `Customer@123` | Active waitlist queue candidate |

To start the application:
```bash
mvn javafx:run
```

---

## 1. Feature 1: Personalised Recommendations & Movie Taste Picker

### 60-Second Demo Walkthrough
1. Log in as `john_doe` / `Customer@123`.
2. Notice the top **"Recommended For You"** hero carousel:
   - Point to the **Match % Badge** (e.g., `98% MATCH`).
   - Point to the **Dynamic Explanation Pill** (e.g., `✦ Because you loved Interstellar` or `✦ Matches your Sci-Fi & English taste`).
3. Click the **"✦ Edit Movie Taste"** button in the header:
   - The glassmorphic **Taste Picker Dialog** slides in.
   - Deselect *Sci-Fi* and select *Drama* + *Hindi*.
   - Adjust the rating slider from `★ 7.5` to `★ 8.5+`.
   - Click **"Save Movie Taste & Personalise →"**.
4. Observe the carousel instantly re-calibrate via background daemon thread: *Oppenheimer* and *Dangal* now appear at the front of the recommendations!

### Viva Theoretical Defense
- **Recency Decay Formula:**
  $$\text{Weight}(\Delta t) = e^{-\lambda \cdot \Delta t}, \quad \text{where } \lambda = \frac{\ln(2)}{30 \text{ days}}$$
  Recent viewings (e.g. 3 days ago) retain $\approx 93\%$ weight, whereas older viewings (>60 days) decay below $25\%$.
- **Composite Scoring Multi-Attribute Function:**
  $$\text{Score}(m) = 0.40 \cdot G(m) + 0.20 \cdot L(m) + 0.20 \cdot \frac{R(m)}{10} + 0.10 \cdot T(m) + 0.10 \cdot P(m)$$
  Where $G(m)$ is genre affinity, $L(m)$ is language affinity, $R(m)$ is movie rating, $T(m)$ is multiplex popularity, and $P(m)$ is explicit rating threshold bonus.
- **Diversity Anti-Clustering Re-Ranker:** A greedy diversity pass enforces a maximum of 2 movies of any single genre in the top results, preventing catalog mono-culture.

---

## 2. Feature 2: Smart Pick Best-Seat Allocation

### 60-Second Demo Walkthrough
1. From the movie catalog, click **"Book Tickets"** on *Dune: Part Two*.
2. Select any upcoming showtime to open the interactive **Seat Map**.
3. In the docked **"Smart Pick"** control panel on the right:
   - Select group size: `3 Seats`.
   - Select preference: `Center Sweet-Spot` (or `Front`, `Back`, `Premium`).
   - Click **"✨ Find Best Seats"**.
4. An acoustic sightline analysis runs in the background. The best ranked recommendations appear with match percentages (e.g., `#1 Recommended: 96% Match`).
5. Hover over a recommendation card: the candidate seats on the map pulse with an animated cyan glow.
6. Click the recommendation card: the seats are selected in a smooth staggered spring animation, and the total updates with an animated numeric roll-up.

### Viva Theoretical Defense
- **Sightline & Distance Formulation:**
  $$\text{Score}(s) = 0.35 \cdot S_{\text{centre}}(s) + 0.35 \cdot S_{\text{view}}(s) + 0.15 \cdot S_{\text{cat}}(s) + 0.15 \cdot S_{\text{pref}}(s)$$
  - $S_{\text{centre}}$ penalizes lateral horizontal deviation from the screen centerline.
  - $S_{\text{view}}$ calculates optimal distance from the screen (sweet-spot at $\approx 65\%$ auditorium depth).
- **Orphan Seat Penalty:** Single isolated empty seats bounded by booked seats or aisles severely damage theater revenue. The group scorer applies a $-25$ point penalty if a proposed allocation leaves an isolated orphan seat.
- **Multi-Row Fallback:** If contiguous blocks on a single row are unavailable, the engine falls back to split clusters or stacked adjacent rows (e.g. 2 seats in Row D, 2 seats directly behind in Row E).

---

## 3. Feature 3: Waitlist System with Auto Seat Offers & In-App Notifications

### 60-Second Demo Walkthrough
1. As a customer, find a sold-out screening or click **"Waitlist ⏳"** on any showtime card.
2. In the **Waitlist Dialog**, specify `2 Seats`, select `Center`, and click **"Join Waitlist Queue →"**.
3. A toast confirms: *"You are in line! You will be notified the instant seats become free."*
4. Go to **"My Bookings"** $\rightarrow$ **"My Waitlist Queue"** tab: view your entry with live status `#1 in line`.
5. In another window or admin session, cancel an existing booking for that showtime.
6. Observe the customer window: within seconds, the **Notification Bell** in the top navigation bar pulses with a red badge.
7. Click the bell to open the slide-in glass **Notification Drawer**:
   - An amber glowing card announces: *"🎟 Seats Available for You! Reserved for 10 minutes."*
   - Click **"Claim Seats →"**: directly loads checkout with your held seats pre-reserved!

### Viva Theoretical Defense
- **ACID Transaction & FIFO Integrity:** Queue entries use `position_ts ASC` timestamps. Queue processing is triggered immediately on `BookingService.cancelBooking` commit, as well as by a daemon thread every 15 seconds.
- **Atomic Two-Phase Offer Holds:** When an offer is generated, seats are locked in `seat_holds` with an expiration timestamp (`offer_expires_at = NOW() + INTERVAL 10 MINUTE`). If the user does not claim the offer within 10 minutes, the background sweeper transitions the status to `EXPIRED`, deletes the hold, and automatically promotes the next person in line.

---

## 4. Feature 4: Cryptographic QR Tickets & Entry Validation Gate

### 60-Second Demo Walkthrough
1. Complete any booking checkout (e.g. 2 seats).
2. The **Ticket Summary View** appears:
   - Displays a glass ticket pass with high-contrast, cryptographically generated **ZXing QR Code**.
   - Click **"Flip Ticket ⟲"** to inspect seating details and audit timestamps in 3D.
   - Click **"Download QR (.png)"** to save the QR pass to your disk.
3. Log out and log in as `admin` / `Admin@123`.
4. Click the **"Entry Gate 🎟"** tab:
   - The scanner input field is automatically focused.
   - Type or paste the token, or click **"📁 Scan QR from Image"** and choose the downloaded PNG ticket.
   - Click **"Verify & Admit Entry"**.
5. **Success Result:** Full-screen animated **GREEN OVERLAY** with checkmark: `ADMITTED — Welcome to CineFlow!`.
6. Click **"Verify & Admit Entry"** a second time with the identical token:
   - **Rejection Result:** Immediate animated **RED SHAKE OVERLAY**: `ALREADY_USED — Ticket already redeemed at 16:42`. Proof of anti-counterfeiting double-entry prevention!

### Viva Theoretical Defense
- **Token Structure:**
  $$\text{CF1}.\langle\text{ticketId}\rangle.\langle\text{nonce}_{16}\rangle.\langle\text{HMAC-SHA256}(\text{payload}, K)_{16}\rangle$$
  - Base64URL-encoded without padding.
  - Constant-time verification (`MessageDigest.isEqual`) prevents cryptographic timing-attack side channels before any SQL query is issued.
- **Atomic Gate Admission Query:**
  ```sql
  UPDATE tickets 
  SET status = 'USED', used_at = NOW(), used_by_user_id = ? 
  WHERE token = ? AND status = 'VALID';
  ```
  If `affected_rows == 1`, admission is granted. If `affected_rows == 0`, a secondary inspection reports whether the ticket was previously used, cancelled, or from an incorrect showtime. Every scan is audited in `entry_scans`.

---

## 5. Feature 5: Rule-Based Dynamic Pricing & Locked Quotes

### 60-Second Demo Walkthrough
1. Log in as `admin` and open the **"Dynamic Pricing ⚡"** tab:
   - On the left: Complete table of configured pricing rules (Occupancy Surge, Early Bird, Prime Evening, Morning Saver).
   - On the right: The **Interactive Live Dynamic Pricing Simulator**.
2. Drag the **Screen Occupancy Slider** from `20%` to `88%`:
   - Watch the price meter adjust in real time from `₹ 250.00` to `₹ 320.00` with an animated counter.
   - The breakdown list shows stacked active rules: `+25% High Demand Surge`, `+15% Weekend Prime`.
3. Drag the **Screening Hour Slider** to `10:00 AM`:
   - Watch the price update dynamically with the `Weekday Morning Discount (-10%)`.
4. Toggle any rule off in the table: the simulator reflects the rule removal immediately.
5. In the customer portal, showtime cards dynamically display **`▲ Surge`** or **`▼ Saver`** chips with tooltips detailing the mathematical breakdown.

### Viva Theoretical Defense
- **Pure Arithmetic & Rounding:**
  $$\text{Adjusted} = \text{BasePrice} \times \left(1 + \sum_{r \in \text{active}} r.\text{adjustment\_pct}\right)$$
  $$\text{Clamped} = \text{clamp}(\text{Adjusted}, \text{BasePrice} \times (1 + \text{floor}), \text{BasePrice} \times (1 + \text{cap}))$$
  $$\text{FinalPrice} = \max\left(₹50.00, \text{roundToNearest5}(\text{Clamped})\right)$$
- All calculations use `BigDecimal` with `RoundingMode.HALF_UP` (no floating-point rounding errors).
- **Deterministic Quote Locking:** When a user selects seats, a signed `PriceQuote` with a 5-minute expiration timestamp is locked in the database. Even if other customers book seats in parallel and push occupancy higher, the customer's quoted checkout price is guaranteed until their hold expires.

---

## 6. Analytical Reports & Performance Metrics

1. Open the **"Analytics"** tab in the Admin Console:
   - **Total Revenue:** Real-time revenue excluding cancelled/refunded transactions.
   - **Dynamic Pricing Uplift:** Additional yield generated through dynamic adjustments (`+₹ 1,840.00`).
   - **Waitlist Conversion Rate:** Percentage of offered waitlist seats converted into paid bookings (`83.3%`).
   - **Gate Pass Rate:** Percentage of successful gate scans vs rejected counterfeits (`98.5%`).
   - **7-Day Revenue BarChart** and **Top Movies PieChart**.
2. Click **"Bookings 📋"** or **"Revenue 💰"**: Exports RFC-4180 compliant CSV files with full escaping.
