package com.cineflow.service;

import com.cineflow.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WaitlistUnitTest {

    @Test
    @DisplayName("Should create WaitlistEntry with default WAITING status and timestamp")
    void testWaitlistEntryCreation() {
        WaitlistEntry entry = new WaitlistEntry(1, 10, 2, SeatPreference.CENTER);

        assertEquals(1, entry.getUserId());
        assertEquals(10, entry.getShowtimeId());
        assertEquals(2, entry.getSeatsWanted());
        assertEquals(SeatPreference.CENTER, entry.getSeatPreference());
        assertEquals(WaitlistStatus.WAITING, entry.getStatus());
        assertNotNull(entry.getPositionTs());
        assertEquals(1, entry.getQueuePosition());
    }

    @Test
    @DisplayName("Should handle status transitions correctly")
    void testStatusTransitions() {
        WaitlistEntry entry = new WaitlistEntry(1, 10, 2, SeatPreference.BEST_OVERALL);
        assertEquals(WaitlistStatus.WAITING, entry.getStatus());

        LocalDateTime now = LocalDateTime.now();
        entry.setStatus(WaitlistStatus.OFFERED);
        entry.setOfferedAt(now);
        entry.setOfferExpiresAt(now.plusMinutes(10));

        assertEquals(WaitlistStatus.OFFERED, entry.getStatus());
        assertNotNull(entry.getOfferedAt());
        assertTrue(entry.getOfferExpiresAt().isAfter(entry.getOfferedAt()));

        entry.setStatus(WaitlistStatus.CLAIMED);
        assertEquals(WaitlistStatus.CLAIMED, entry.getStatus());
    }

    @Test
    @DisplayName("Should create Notification with unread state and timestamps")
    void testNotificationCreation() {
        Notification n = new Notification(
                2,
                NotificationType.WAITLIST_OFFER,
                "Seat Offer",
                "Seats available",
                "WAITLIST",
                42
        );

        assertEquals(2, n.getUserId());
        assertEquals(NotificationType.WAITLIST_OFFER, n.getType());
        assertEquals("Seat Offer", n.getTitle());
        assertEquals(Integer.valueOf(42), n.getRefId());
        assertFalse(n.isRead());
        assertNotNull(n.getCreatedAt());

        n.setRead(true);
        assertTrue(n.isRead());
    }

    @Test
    @DisplayName("Default preference fallback should be BEST_OVERALL")
    void testNullPreferenceFallback() {
        WaitlistEntry entry = new WaitlistEntry(1, 10, 3, null);
        assertEquals(SeatPreference.BEST_OVERALL, entry.getSeatPreference());
    }
}
