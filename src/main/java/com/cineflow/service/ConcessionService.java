package com.cineflow.service;

import com.cineflow.dao.ConcessionDAO;
import com.cineflow.model.BookingConcession;
import com.cineflow.model.Concession;
import com.cineflow.model.ConcessionPickupStatus;

import java.sql.SQLException;
import java.util.List;

/**
 * CineFlow v3 — Business Service for F&B theater concessions and pre-orders.
 */
public class ConcessionService {

    private final ConcessionDAO concessionDAO;

    public ConcessionService() {
        this.concessionDAO = new ConcessionDAO();
    }

    public ConcessionService(ConcessionDAO concessionDAO) {
        this.concessionDAO = concessionDAO;
    }

    public List<Concession> getActiveConcessions() throws SQLException {
        return concessionDAO.getAllActive();
    }

    public List<Concession> getAllConcessions() throws SQLException {
        return concessionDAO.getAll();
    }

    public List<BookingConcession> getBookingConcessions(int bookingId) throws SQLException {
        return concessionDAO.findByBookingId(bookingId);
    }

    public List<BookingConcession> getAllConcessionOrders() throws SQLException {
        return concessionDAO.findAllOrders();
    }

    public void placeConcessionOrder(int bookingId, List<BookingConcession> items) throws SQLException {
        concessionDAO.addItemsToBooking(bookingId, items);
    }

    public boolean updatePickupStatus(int bookingConcessionId, ConcessionPickupStatus newStatus) throws SQLException {
        return concessionDAO.updatePickupStatus(bookingConcessionId, newStatus);
    }
}
