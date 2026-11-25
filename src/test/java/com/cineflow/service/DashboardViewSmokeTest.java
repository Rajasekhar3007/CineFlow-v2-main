package com.cineflow.service;

import com.cineflow.dao.BookingDAO;
import com.cineflow.model.Booking;
import com.cineflow.model.Role;
import com.cineflow.model.User;
import com.cineflow.ui.admin.AdminDashboardView;
import com.cineflow.ui.customer.CustomerDashboardView;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UI Initialization Smoke Test")
class DashboardViewSmokeTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
    }

    @Test
    @DisplayName("BookingDAO.findAllJoined should execute without error")
    void testBookingDAOFindAllJoined() {
        BookingDAO dao = new BookingDAO();
        assertDoesNotThrow(() -> {
            List<Booking> bookings = dao.findAllJoined(null, null, null, null, null);
            assertNotNull(bookings);
            System.out.println("Total admin bookings loaded: " + bookings.size());
        });
    }

    @Test
    @DisplayName("AdminDashboardView and all tabs should instantiate without throwing")
    void testAdminDashboardViewInstantiation() {
        User admin = new User(1, "admin", "hash", "salt", "System Admin", "admin@cineflow.com", Role.ADMIN, true);
        assertDoesNotThrow(() -> {
            AdminDashboardView view = new AdminDashboardView(admin);
            assertNotNull(view.getView());
        });
    }

    @Test
    @DisplayName("CustomerDashboardView should instantiate without throwing")
    void testCustomerDashboardViewInstantiation() {
        User customer = new User(2, "john_doe", "hash", "salt", "John Doe", "john@cineflow.com", Role.CUSTOMER, true);
        assertDoesNotThrow(() -> {
            CustomerDashboardView view = new CustomerDashboardView(customer);
            assertNotNull(view.getView());
        });
    }
}
