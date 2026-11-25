package com.cineflow.service;

import com.cineflow.util.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayServiceTest {

    private final RazorpayService razorpayService = new RazorpayService();

    @Test
    @DisplayName("Should create real-time Razorpay Order via live REST API")
    void testCreateOrderLive() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(AppConfig.isRazorpayConfigured()
                && AppConfig.getRazorpayKeyId().startsWith("rzp_test_"),
                "Razorpay test credentials are required");
        BigDecimal amount = new BigDecimal("250.00");
        String receipt = "test_rcpt_" + System.currentTimeMillis();

        RazorpayService.RazorpayOrder order = razorpayService.createOrder(amount, receipt);

        assertNotNull(order);
        assertNotNull(order.getOrderId(), "Order ID must not be null");
        assertTrue(order.getOrderId().startsWith("order_"), "Razorpay order ID must start with 'order_' prefix");
        assertEquals(25000L, order.getAmountPaise(), "250.00 INR must convert to 25000 paise");
        assertEquals(new BigDecimal("250.00"), order.getAmountRupees());
        assertEquals("INR", order.getCurrency());
    }

    @Test
    @DisplayName("Should verify cryptographic HMAC-SHA256 signature matching Razorpay specification")
    void testHmacSha256Verification() {
        String orderId = "order_EKwxwAgItmmMnv";
        String paymentId = "pay_G3U9a4QxRsmq7y";

        String signature = razorpayService.generateSignature(orderId, paymentId);
        assertNotNull(signature);
        assertFalse(signature.isBlank());

        // Verify valid signature passes
        assertTrue(razorpayService.verifyPaymentSignature(orderId, paymentId, signature));

        // Verify tampered signature fails
        assertFalse(razorpayService.verifyPaymentSignature(orderId, paymentId, "invalid_tampered_signature_hex"));
        assertFalse(razorpayService.verifyPaymentSignature(orderId, "pay_different_id", signature));
    }

    @Test
    @DisplayName("Should support instant test payment simulation with verified signature")
    void testSimulatedTestPayment() {
        String orderId = "order_test_123456";
        BigDecimal amount = new BigDecimal("499.00");

        RazorpayService.RazorpayPaymentResult result = razorpayService.simulateTestPayment(orderId, amount);

        assertNotNull(result);
        assertEquals(orderId, result.getOrderId());
        assertNotNull(result.getPaymentId());
        assertTrue(result.getPaymentId().startsWith("pay_sim_"));
        assertTrue(result.isVerified(), "Simulated payment must pass cryptographic verification");
    }
}
