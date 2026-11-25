package com.cineflow.ui.common;

import com.cineflow.dao.SeatHoldDAO;
import com.cineflow.service.BookingService;

import java.sql.SQLException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * CineFlow v2 Central Background Jobs Manager.
 * Orchestrates periodic sweepers (hold expiries, waitlist offer timeouts, notification polling)
 * across daemon threads without impacting GUI responsiveness.
 */
public class BackgroundJobs {

    private static ScheduledExecutorService scheduler;
    private static final SeatHoldDAO seatHoldDAO = new SeatHoldDAO();

    /**
     * Initializes and starts background daemon sweepers.
     */
    public static synchronized void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newScheduledThreadPool(3, runnable -> {
            Thread t = new Thread(runnable, "CineFlow-BackgroundScheduler");
            t.setDaemon(true);
            return t;
        });

        // Periodic sweeper 1: Clean up expired seat holds every 15 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int cleaned = seatHoldDAO.deleteExpiredHolds();
                if (cleaned > 0) {
                    System.out.println("[BackgroundJobs] Swept " + cleaned + " expired seat holds.");
                }
            } catch (Exception e) {
                System.err.println("[BackgroundJobs] Expired hold sweep error: " + e.getMessage());
            }
        }, 5, 15, TimeUnit.SECONDS);

        // Periodic sweeper 2: Expire unaccepted waitlist offers and sweep waiting queues every 15 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                com.cineflow.service.WaitlistService.getInstance().expireOffers();
                com.cineflow.service.WaitlistService.getInstance().processAllWaitingQueues();
            } catch (Exception e) {
                System.err.println("[BackgroundJobs] Waitlist sweep error: " + e.getMessage());
            }
        }, 10, 15, TimeUnit.SECONDS);

        BookingService bookingService = new BookingService();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                bookingService.processPendingRefunds();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (SQLException e) {
                System.err.println("[BackgroundJobs] Refund processing error: " + e.getMessage());
            }
        }, 5, 15, TimeUnit.SECONDS);

        System.out.println("[BackgroundJobs] CineFlow v2 background scheduler started.");
    }

    /**
     * Registers a recurring daemon background job.
     */
    public static synchronized ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (scheduler == null || scheduler.isShutdown()) {
            start();
        }
        return scheduler.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    /**
     * Gracefully shuts down the background scheduler.
     */
    public static synchronized void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            System.out.println("[BackgroundJobs] Background scheduler stopped.");
        }
    }
}
