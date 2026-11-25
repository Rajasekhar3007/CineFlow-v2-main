package com.cineflow.service;

import com.cineflow.model.Seat;
import com.cineflow.model.SeatPreference;
import com.cineflow.model.SeatSuggestion;
import com.cineflow.model.SeatType;

import java.util.*;

/**
 * CineFlow v2 Pure Smart Pick Seat Scoring & Allocation Engine.
 * Evaluates auditorium geometry, viewing distances, centre alignment, and orphan penalties.
 */
public class SeatScorer {

    /**
     * Finds up to 3 optimal seat group suggestions matching customer preferences.
     */
    public static List<SeatSuggestion> suggestSeats(List<Seat> allSeats,
                                                    Set<Integer> unavailableSeatIds,
                                                    int count,
                                                    SeatPreference preference,
                                                    int totalRows,
                                                    int seatsPerRow) {
        if (allSeats == null || allSeats.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        final SeatPreference effectivePref = preference != null ? preference : SeatPreference.BEST_OVERALL;

        // Group seats by row label
        Map<String, List<Seat>> rowMap = new LinkedHashMap<>();
        for (Seat s : allSeats) {
            rowMap.computeIfAbsent(s.getRowLabel(), k -> new ArrayList<>()).add(s);
        }
        for (List<Seat> rList : rowMap.values()) {
            rList.sort(Comparator.comparingInt(Seat::getSeatNumber));
        }

        // Count total free seats
        long totalFree = allSeats.stream().filter(s -> !unavailableSeatIds.contains(s.getId())).count();
        if (totalFree < count) {
            return List.of(SeatSuggestion.notEnoughSeats(
                    String.format("Only %d seats remaining in this showtime. Join the waitlist for automatic offers!", totalFree)
            ));
        }

        // Target row calculation
        double targetRowIndex = switch (effectivePref) {
            case FRONT -> Math.max(1.0, totalRows * 0.25);
            case BACK, PREMIUM_ONLY -> Math.max(1.0, totalRows * 0.90);
            case CENTER, BEST_OVERALL -> Math.max(1.0, totalRows * 0.65);
        };

        // 1. Contiguous Window Search
        List<ScoredGroup> contiguousCandidates = new ArrayList<>();

        for (Map.Entry<String, List<Seat>> entry : rowMap.entrySet()) {
            List<Seat> rowSeats = entry.getValue();
            if (rowSeats.size() < count) continue;

            for (int i = 0; i <= rowSeats.size() - count; i++) {
                List<Seat> window = rowSeats.subList(i, i + count);
                boolean allAvailable = true;
                for (Seat s : window) {
                    if (unavailableSeatIds.contains(s.getId())) {
                        allAvailable = false;
                        break;
                    }
                }
                if (!allAvailable) continue;

                // If preference is PREMIUM_ONLY, require all seats in window to be PREMIUM
                if (effectivePref == SeatPreference.PREMIUM_ONLY) {
                    boolean allPrem = window.stream().allMatch(s -> s.getSeatType() == SeatType.PREMIUM);
                    if (!allPrem) continue;
                }

                double score = scoreSeatGroup(window, rowSeats, i, count, effectivePref, targetRowIndex, totalRows, seatsPerRow);
                String reason = buildReason(window, effectivePref, true, false);
                contiguousCandidates.add(new ScoredGroup(window, score, reason, true, false));
            }
        }

        contiguousCandidates.sort(Comparator.comparingDouble(ScoredGroup::score).reversed());

        List<SeatSuggestion> results = new ArrayList<>();
        // Select top non-overlapping or distinct-row contiguous candidates
        Set<String> seenRows = new HashSet<>();
        for (ScoredGroup g : contiguousCandidates) {
            if (results.size() >= 3) break;
            String row = g.seats().get(0).getRowLabel();
            if (!seenRows.contains(row) || results.size() < 2) {
                seenRows.add(row);
                results.add(new SeatSuggestion(g.seats(), g.score(), g.reason(), true, false, false));
            }
        }

        if (!results.isEmpty()) {
            return results;
        }

        // 2. Fallback: Split Group across same row
        List<ScoredGroup> splitCandidates = findSplitRowGroups(rowMap, unavailableSeatIds, count, effectivePref, targetRowIndex, totalRows, seatsPerRow);
        if (!splitCandidates.isEmpty()) {
            for (ScoredGroup g : splitCandidates) {
                if (results.size() >= 3) break;
                results.add(new SeatSuggestion(g.seats(), g.score(), g.reason(), false, true, false));
            }
            return results;
        }

        // 3. Fallback: Stacked Groups across adjacent rows
        List<ScoredGroup> stackedCandidates = findStackedRowGroups(rowMap, unavailableSeatIds, count, effectivePref, targetRowIndex, totalRows, seatsPerRow);
        if (!stackedCandidates.isEmpty()) {
            for (ScoredGroup g : stackedCandidates) {
                if (results.size() >= 3) break;
                results.add(new SeatSuggestion(g.seats(), g.score(), g.reason(), false, true, false));
            }
            return results;
        }

        // 4. Final Fallback: Best individual seats
        List<Seat> individualSeats = allSeats.stream()
                .filter(s -> !unavailableSeatIds.contains(s.getId()))
                .sorted(Comparator.comparingDouble((Seat s) -> scoreSingleSeat(s, effectivePref, targetRowIndex, totalRows, seatsPerRow)).reversed())
                .limit(count)
                .toList();

        if (individualSeats.size() == count) {
            results.add(new SeatSuggestion(individualSeats, 50.0,
                    "Dispersed best available individual seats (no adjacent block found)", false, true, false));
        }

        return results;
    }

    private static double scoreSeatGroup(List<Seat> group, List<Seat> rowSeats, int startIndex, int count,
                                         SeatPreference pref, double targetRow, int totalRows, int seatsPerRow) {
        double sum = 0;
        for (Seat s : group) {
            sum += scoreSingleSeat(s, pref, targetRow, totalRows, seatsPerRow);
        }
        double baseScore = sum / count;

        // Orphan seat check on left
        double penalty = 0;
        if (startIndex == 1) {
            // Left seat is index 0. If available, it is an isolated single seat against the wall
            if (!rowSeats.isEmpty()) penalty += 25.0;
        } else if (startIndex > 1) {
            // Check if leaving a single isolated seat between occupied seat and this group
            // (e.g. index-1 is free, but index-2 is occupied)
        }

        // Orphan check on right
        int rightIndex = startIndex + count;
        if (rightIndex == rowSeats.size() - 1) {
            penalty += 25.0; // Leaves single isolated seat on the right edge
        }

        return Math.max(10.0, baseScore - penalty);
    }

    public static double scoreSingleSeat(Seat seat, SeatPreference pref, double targetRow, int totalRows, int seatsPerRow) {
        // 1. Centre alignment (35%)
        double midCol = (seatsPerRow + 1) / 2.0;
        double colDist = Math.abs(seat.getSeatNumber() - midCol);
        double maxColDist = Math.max(1.0, seatsPerRow / 2.0);
        double centreScore = Math.max(0.0, 100.0 - (colDist / maxColDist) * 75.0);

        // 2. Viewing distance / sweet spot row (35%)
        int rowIndex = getRowIndex(seat.getRowLabel());
        double rowDist = Math.abs(rowIndex - targetRow);
        double viewScore = Math.max(0.0, 100.0 - (rowDist / Math.max(1.0, totalRows)) * 80.0);

        // 3. Category bonus (15%)
        double catScore = seat.getSeatType() == SeatType.PREMIUM ? 100.0 : 60.0;
        if (pref == SeatPreference.PREMIUM_ONLY && seat.getSeatType() != SeatType.PREMIUM) {
            catScore = 0.0;
        }

        // 4. Preference alignment (15%)
        double prefScore = 75.0;
        if (pref == SeatPreference.CENTER && colDist <= 1.5) prefScore = 100.0;
        if (pref == SeatPreference.FRONT && rowIndex <= totalRows * 0.35) prefScore = 100.0;
        if (pref == SeatPreference.BACK && rowIndex >= totalRows * 0.70) prefScore = 100.0;

        return (centreScore * 0.35) + (viewScore * 0.35) + (catScore * 0.15) + (prefScore * 0.15);
    }

    private static List<ScoredGroup> findSplitRowGroups(Map<String, List<Seat>> rowMap, Set<Integer> unavailable,
                                                        int count, SeatPreference pref, double targetRow,
                                                        int totalRows, int seatsPerRow) {
        List<ScoredGroup> candidates = new ArrayList<>();
        int half = count / 2;
        int rest = count - half;

        for (Map.Entry<String, List<Seat>> entry : rowMap.entrySet()) {
            List<Seat> freeInRow = entry.getValue().stream()
                    .filter(s -> !unavailable.contains(s.getId()))
                    .toList();
            if (freeInRow.size() >= count) {
                List<Seat> group = freeInRow.subList(0, count);
                double score = group.stream().mapToDouble(s -> scoreSingleSeat(s, pref, targetRow, totalRows, seatsPerRow)).average().orElse(50.0);
                candidates.add(new ScoredGroup(group, score - 15.0, "Split block across Row " + entry.getKey(), false, true));
            }
        }
        candidates.sort(Comparator.comparingDouble(ScoredGroup::score).reversed());
        return candidates;
    }

    private static List<ScoredGroup> findStackedRowGroups(Map<String, List<Seat>> rowMap, Set<Integer> unavailable,
                                                          int count, SeatPreference pref, double targetRow,
                                                          int totalRows, int seatsPerRow) {
        List<ScoredGroup> candidates = new ArrayList<>();
        List<String> rowLabels = new ArrayList<>(rowMap.keySet());
        int perRow = (int) Math.ceil(count / 2.0);

        for (int i = 0; i < rowLabels.size() - 1; i++) {
            List<Seat> r1 = rowMap.get(rowLabels.get(i)).stream().filter(s -> !unavailable.contains(s.getId())).toList();
            List<Seat> r2 = rowMap.get(rowLabels.get(i + 1)).stream().filter(s -> !unavailable.contains(s.getId())).toList();
            if (r1.size() >= perRow && r2.size() >= (count - perRow)) {
                List<Seat> group = new ArrayList<>();
                group.addAll(r1.subList(0, perRow));
                group.addAll(r2.subList(0, count - perRow));
                double score = group.stream().mapToDouble(s -> scoreSingleSeat(s, pref, targetRow, totalRows, seatsPerRow)).average().orElse(50.0);
                candidates.add(new ScoredGroup(group, score - 12.0,
                        "Stacked across Rows " + rowLabels.get(i) + " & " + rowLabels.get(i + 1), false, true));
            }
        }
        candidates.sort(Comparator.comparingDouble(ScoredGroup::score).reversed());
        return candidates;
    }

    private static int getRowIndex(String label) {
        if (label == null || label.isEmpty()) return 1;
        char c = Character.toUpperCase(label.charAt(0));
        return Math.max(1, c - 'A' + 1);
    }

    private static String buildReason(List<Seat> seats, SeatPreference pref, boolean contiguous, boolean split) {
        if (seats.isEmpty()) return "";
        String row = seats.get(0).getRowLabel();
        boolean allPrem = seats.stream().allMatch(s -> s.getSeatType() == SeatType.PREMIUM);
        String tier = allPrem ? "All Premium" : "Regular";
        return String.format("Row %s, contiguous group of %d (%s) • %s", row, seats.size(), tier, pref.getDescription());
    }

    private record ScoredGroup(List<Seat> seats, double score, String reason, boolean contiguous, boolean split) {}
}
