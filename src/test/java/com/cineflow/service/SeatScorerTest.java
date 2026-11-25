package com.cineflow.service;

import com.cineflow.model.Seat;
import com.cineflow.model.SeatPreference;
import com.cineflow.model.SeatSuggestion;
import com.cineflow.model.SeatType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SeatScorerTest {

    private List<Seat> sampleSeats;
    private final int totalRows = 5;      // Rows A, B, C, D, E
    private final int seatsPerRow = 8;    // Columns 1-8

    @BeforeEach
    void setUp() {
        sampleSeats = new ArrayList<>();
        int id = 1;
        char[] rowLabels = {'A', 'B', 'C', 'D', 'E'};
        for (char r : rowLabels) {
            for (int c = 1; c <= seatsPerRow; c++) {
                SeatType type = (r == 'E') ? SeatType.PREMIUM : SeatType.REGULAR;
                sampleSeats.add(new Seat(id++, 1, String.valueOf(r), c, type));
            }
        }
    }

    @Test
    @DisplayName("Should select contiguous centre seats in sweet-spot row for BEST_OVERALL")
    void testBestOverallCentreAllocation() {
        Set<Integer> unavailable = new HashSet<>();
        List<SeatSuggestion> suggestions = SeatScorer.suggestSeats(
                sampleSeats, unavailable, 4, SeatPreference.BEST_OVERALL, totalRows, seatsPerRow
        );

        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());
        SeatSuggestion best = suggestions.get(0);
        assertTrue(best.isContiguous());
        assertEquals(4, best.getSeats().size());

        // Row C is ~65% sweet spot row in 5 rows
        assertEquals("C", best.getSeats().get(0).getRowLabel());
        // Columns should be centered (e.g. 3, 4, 5, 6)
        assertEquals(3, best.getSeats().get(0).getSeatNumber());
        assertEquals(6, best.getSeats().get(3).getSeatNumber());
    }

    @Test
    @DisplayName("Should prefer front rows when FRONT preference is requested")
    void testFrontRowPreference() {
        Set<Integer> unavailable = new HashSet<>();
        List<SeatSuggestion> suggestions = SeatScorer.suggestSeats(
                sampleSeats, unavailable, 2, SeatPreference.FRONT, totalRows, seatsPerRow
        );

        assertFalse(suggestions.isEmpty());
        SeatSuggestion best = suggestions.get(0);
        assertTrue(best.isContiguous());
        // Front row is Row A or B
        String row = best.getSeats().get(0).getRowLabel();
        assertTrue("A".equals(row) || "B".equals(row));
    }

    @Test
    @DisplayName("Should prefer premium seats when PREMIUM_ONLY preference is requested")
    void testPremiumOnlyPreference() {
        Set<Integer> unavailable = new HashSet<>();
        List<SeatSuggestion> suggestions = SeatScorer.suggestSeats(
                sampleSeats, unavailable, 2, SeatPreference.PREMIUM_ONLY, totalRows, seatsPerRow
        );

        assertFalse(suggestions.isEmpty());
        SeatSuggestion best = suggestions.get(0);
        assertEquals("E", best.getSeats().get(0).getRowLabel());
        assertTrue(best.getSeats().stream().allMatch(s -> s.getSeatType() == SeatType.PREMIUM));
    }

    @Test
    @DisplayName("Should avoid booked and held seats")
    void testExcludeUnavailableSeats() {
        Set<Integer> unavailable = new HashSet<>();
        // Mark all seats in Row C as unavailable
        for (Seat s : sampleSeats) {
            if ("C".equals(s.getRowLabel())) {
                unavailable.add(s.getId());
            }
        }

        List<SeatSuggestion> suggestions = SeatScorer.suggestSeats(
                sampleSeats, unavailable, 4, SeatPreference.BEST_OVERALL, totalRows, seatsPerRow
        );

        assertFalse(suggestions.isEmpty());
        SeatSuggestion best = suggestions.get(0);
        // Should choose adjacent Row D or B
        assertNotEquals("C", best.getSeats().get(0).getRowLabel());
    }

    @Test
    @DisplayName("Should return NOT_ENOUGH_SEATS when requested count exceeds capacity")
    void testNotEnoughSeats() {
        Set<Integer> unavailable = new HashSet<>();
        // Mark 38 seats out of 40 unavailable -> only 2 free
        for (int i = 0; i < 38; i++) {
            unavailable.add(sampleSeats.get(i).getId());
        }

        List<SeatSuggestion> suggestions = SeatScorer.suggestSeats(
                sampleSeats, unavailable, 4, SeatPreference.BEST_OVERALL, totalRows, seatsPerRow
        );

        assertNotNull(suggestions);
        assertEquals(1, suggestions.size());
        assertTrue(suggestions.get(0).isNotEnoughSeats());
        assertTrue(suggestions.get(0).getReason().contains("waitlist"));
    }

    @Test
    @DisplayName("Should correctly handle single seat selection")
    void testSingleSeatSelection() {
        Set<Integer> unavailable = new HashSet<>();
        List<SeatSuggestion> suggestions = SeatScorer.suggestSeats(
                sampleSeats, unavailable, 1, SeatPreference.CENTER, totalRows, seatsPerRow
        );

        assertFalse(suggestions.isEmpty());
        SeatSuggestion best = suggestions.get(0);
        assertEquals(1, best.getSeats().size());
        int col = best.getSeats().get(0).getSeatNumber();
        // Mid column in 8 is 4 or 5
        assertTrue(col == 4 || col == 5);
    }
}
