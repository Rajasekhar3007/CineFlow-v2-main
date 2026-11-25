package com.cineflow.service;

import com.cineflow.model.User;
import com.cineflow.util.AppConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CineFlow — Real-Time Razorpay Payment Gateway Service.
 *
 * Implements:
 *  1. Orders API integration (POST https://api.razorpay.com/v1/orders) using Java 21 HttpClient.
 *  2. Cryptographic HMAC-SHA256 signature verification matching Razorpay security specification.
 *  3. In-memory temporary HTTP checkout bridge (com.sun.net.httpserver) serving
 *     official checkout.js popup with seamless browser redirection.
 *  4. Instant test payment simulator for headless or rapid QA testing.
 */
public class RazorpayService {

    private static final String ORDERS_ENDPOINT = "https://api.razorpay.com/v1/orders";
    private static final String PAYMENTS_ENDPOINT = "https://api.razorpay.com/v1/payments/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ─────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────
    public static class RazorpayOrder {
        private final String orderId;
        private final long amountPaise;
        private final String currency;
        private final String receipt;
        private final String status;

        public RazorpayOrder(String orderId, long amountPaise, String currency, String receipt, String status) {
            this.orderId = orderId;
            this.amountPaise = amountPaise;
            this.currency = currency;
            this.receipt = receipt;
            this.status = status;
        }

        public String getOrderId() { return orderId; }
        public long getAmountPaise() { return amountPaise; }
        public BigDecimal getAmountRupees() {
            return BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        public String getCurrency() { return currency; }
        public String getReceipt() { return receipt; }
        public String getStatus() { return status; }
    }

    public static class RazorpayPaymentResult {
        private final String orderId;
        private final String paymentId;
        private final String signature;
        private final boolean verified;
        private final String message;

        public RazorpayPaymentResult(String orderId, String paymentId, String signature, boolean verified, String message) {
            this.orderId = orderId;
            this.paymentId = paymentId;
            this.signature = signature;
            this.verified = verified;
            this.message = message;
        }

        public String getOrderId() { return orderId; }
        public String getPaymentId() { return paymentId; }
        public String getSignature() { return signature; }
        public boolean isVerified() { return verified; }
        public String getMessage() { return message; }
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. ORDER CREATION VIA RAZORPAY REST API
    // ─────────────────────────────────────────────────────────────────
    record Refund(String id, String status) {}

    Refund refundPayment(String paymentId, String refundId) throws IOException, InterruptedException {
        if (!paymentId.matches("pay_[A-Za-z0-9]+")
                || (refundId != null && !refundId.matches("rfnd_[A-Za-z0-9]+"))) {
            throw new IOException("Invalid Razorpay refund reference.");
        }
        requireGatewayCredentials();
        String endpoint = PAYMENTS_ENDPOINT + paymentId;
        String credentials = AppConfig.getRazorpayKeyId() + ":" + AppConfig.getRazorpayKeySecret();
        String authorization = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .header("Authorization", "Basic " + authorization)
                .timeout(Duration.ofSeconds(12));
        if (refundId == null) {
            // A stable key lets a retry recover the original refund after an interrupted request.
            request.uri(URI.create(endpoint + "/refund"))
                    .header("Content-Type", "application/json")
                    .header("X-Refund-Idempotency", "cineflow-cancel-" + paymentId)
                    .POST(HttpRequest.BodyPublishers.ofString("{}"));
        } else {
            request.uri(URI.create(endpoint + "/refunds/" + refundId)).GET();
        }

        HttpResponse<String> response = HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Razorpay refund request returned HTTP " + response.statusCode());
        }
        String id = extractJsonField(response.body(), "id");
        String status = extractJsonField(response.body(), "status");
        if (id == null || !id.matches("rfnd_[A-Za-z0-9]+")
                || !paymentId.equals(extractJsonField(response.body(), "payment_id"))
                || (refundId != null && !refundId.equals(id))
                || !("pending".equals(status) || "processed".equals(status) || "failed".equals(status))) {
            throw new IOException("Unexpected Razorpay refund response.");
        }
        return new Refund(id, status);
    }

    private void requireGatewayCredentials() throws IOException {
        if (!AppConfig.isRazorpayConfigured()) {
            throw new IOException("Set razorpay.key.id and razorpay.key.secret in db.properties to use the gateway.");
        }
    }

    public RazorpayOrder createOrder(BigDecimal amountRupees, String receipt) throws Exception {
        requireGatewayCredentials();
        if (amountRupees == null || amountRupees.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order amount must be greater than zero.");
        }
        long amountPaise = amountRupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        String safeReceipt = (receipt != null && !receipt.isBlank()) ? receipt : "rcpt_" + System.currentTimeMillis();

        String keyId = AppConfig.getRazorpayKeyId();
        String keySecret = AppConfig.getRazorpayKeySecret();

        String jsonPayload = String.format(
                "{\"amount\":%d,\"currency\":\"INR\",\"receipt\":\"%s\",\"notes\":{\"app\":\"CineFlow\"}}",
                amountPaise, safeReceipt
        );

        String basicAuth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ORDERS_ENDPOINT))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(12))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            String body = response.body();
            String id = extractJsonField(body, "id");
            String status = extractJsonField(body, "status");
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Failed to parse Razorpay Order ID from response: " + body);
            }
            return new RazorpayOrder(id, amountPaise, "INR", safeReceipt, status != null ? status : "created");
        } else {
            throw new RuntimeException("Razorpay Orders API Error [" + response.statusCode() + "]: " + response.body());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. CRYPTOGRAPHIC SIGNATURE VERIFICATION (HMAC-SHA256)
    // ─────────────────────────────────────────────────────────────────
    public boolean verifyPaymentSignature(String orderId, String paymentId, String razorpaySignature) {
        if (orderId == null || paymentId == null || razorpaySignature == null) return false;
        try {
            if (paymentId.startsWith("pay_sim_") && AppConfig.getRazorpayKeyId().startsWith("rzp_live_")) {
                return false;
            }
            if ("simulated_test".equals(razorpaySignature) && paymentId.startsWith("pay_sim_")) {
                return true;
            }
            String keySecret = AppConfig.getRazorpayKeySecret();
            String generatedSignature = calculateHmacSha256(orderId + "|" + paymentId, keySecret);
            return MessageDigest.isEqual(
                    generatedSignature.toLowerCase().getBytes(StandardCharsets.UTF_8),
                    razorpaySignature.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            System.err.println("[RazorpayService] Signature verification exception: " + e.getMessage());
            return false;
        }
    }

    public String generateSignature(String orderId, String paymentId) {
        String keySecret = AppConfig.getRazorpayKeySecret();
        return calculateHmacSha256(orderId + "|" + paymentId, keySecret);
    }

    public static String calculateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hmacBytes.length * 2);
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Could not compute HMAC-SHA256: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. RAPID TEST SIMULATOR (Instant 1-Click QA)
    // ─────────────────────────────────────────────────────────────────
    public RazorpayPaymentResult simulateTestPayment(String orderId, BigDecimal amountRupees) {
        String paymentId = "pay_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String signature = generateSignature(orderId, paymentId);
        boolean verified = verifyPaymentSignature(orderId, paymentId, signature);
        return new RazorpayPaymentResult(orderId, paymentId, signature, verified, "Simulated instant test payment authorized successfully.");
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. REAL-TIME LOCAL CHECKOUT SERVER BRIDGE
    // ─────────────────────────────────────────────────────────────────
    public static class CheckoutServerHandle {
        private final HttpServer server;
        private final int port;
        private final String checkoutUrl;

        public CheckoutServerHandle(HttpServer server, int port, String checkoutUrl) {
            this.server = server;
            this.port = port;
            this.checkoutUrl = checkoutUrl;
        }

        public int getPort() { return port; }
        public String getCheckoutUrl() { return checkoutUrl; }

        public void stop() {
            try {
                server.stop(0);
            } catch (Exception ignored) {}
        }
    }

    public CheckoutServerHandle startCheckoutBridge(
            RazorpayOrder order,
            User user,
            String movieTitle,
            Consumer<RazorpayPaymentResult> onPaymentComplete,
            Consumer<String> onPaymentCancelled
    ) throws IOException {

        // Bind to ephemeral port
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String checkoutUrl = "http://localhost:" + port + "/checkout";

        // /checkout handler
        server.createContext("/checkout", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String html = buildCheckoutHtml(order, user, movieTitle, port);
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        // /payment-callback handler
        server.createContext("/payment-callback", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String body = readBody(exchange.getRequestBody());
                String pId = extractJsonField(body, "razorpay_payment_id");
                String oId = extractJsonField(body, "razorpay_order_id");
                String sig = extractJsonField(body, "razorpay_signature");

                if (oId == null || oId.isBlank()) oId = order.getOrderId();

                boolean valid = verifyPaymentSignature(oId, pId, sig);

                String jsonResponse = "{\"status\":\"" + (valid ? "success" : "failed") + "\"}";
                byte[] respBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }

                RazorpayPaymentResult res = new RazorpayPaymentResult(
                        oId, pId, sig, valid, valid ? "Payment verified successfully" : "HMAC signature mismatch"
                );

                // Stop server after brief delay and notify listener
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    server.stop(0);
                }).start();

                if (onPaymentComplete != null) {
                    onPaymentComplete.accept(res);
                }
            }
        });

        // /payment-cancelled handler
        server.createContext("/payment-cancelled", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(204, -1);
                if (onPaymentCancelled != null) {
                    onPaymentCancelled.accept("User closed Razorpay checkout modal.");
                }
            }
        });

        server.setExecutor(null); // default executor
        server.start();

        return new CheckoutServerHandle(server, port, checkoutUrl);
    }

    public static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {}

        // Fallbacks for Windows / macOS / Linux
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            System.err.println("[RazorpayService] Could not auto-open browser: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // HTML CHECKOUT BUILDER
    // ─────────────────────────────────────────────────────────────────
    private String buildCheckoutHtml(RazorpayOrder order, User user, String movieTitle, int port) {
        String keyId = AppConfig.getRazorpayKeyId();
        String customerName = user != null ? user.getFullName() : "CineFlow Guest";
        String customerEmail = user != null ? user.getEmail() : "customer@cineflow.com";
        String customerPhone = "9999999999";
        String safeTitle = movieTitle != null ? movieTitle.replace("\"", "\\\"") : "Movie Ticket";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\" />\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n" +
                "  <title>CineFlow &bull; Razorpay Secure Payment</title>\n" +
                "  <script src=\"https://checkout.razorpay.com/v1/checkout.js\"></script>\n" +
                "  <style>\n" +
                "    body {\n" +
                "      background-color: #0B0914;\n" +
                "      color: #F8FAFC;\n" +
                "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      min-height: 100vh;\n" +
                "      margin: 0;\n" +
                "    }\n" +
                "    .card {\n" +
                "      background: #141126;\n" +
                "      border: 1px solid rgba(139, 92, 246, 0.3);\n" +
                "      border-radius: 16px;\n" +
                "      padding: 32px 36px;\n" +
                "      max-width: 460px;\n" +
                "      width: 100%;\n" +
                "      box-shadow: 0 20px 40px rgba(0,0,0,0.6), 0 0 30px rgba(139,92,246,0.15);\n" +
                "      text-align: center;\n" +
                "    }\n" +
                "    .badge {\n" +
                "      display: inline-block;\n" +
                "      font-size: 11px;\n" +
                "      font-weight: 700;\n" +
                "      color: #00C2FF;\n" +
                "      background: rgba(0,194,255,0.1);\n" +
                "      border: 1px solid rgba(0,194,255,0.3);\n" +
                "      border-radius: 20px;\n" +
                "      padding: 4px 12px;\n" +
                "      margin-bottom: 12px;\n" +
                "    }\n" +
                "    h1 { font-size: 22px; margin: 0 0 8px; font-weight: 800; }\n" +
                "    p { color: #A1A1AA; font-size: 14px; margin: 0 0 20px; }\n" +
                "    .amount-box {\n" +
                "      background: rgba(139,92,246,0.08);\n" +
                "      border: 1px dashed rgba(139,92,246,0.35);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 16px;\n" +
                "      margin-bottom: 24px;\n" +
                "    }\n" +
                "    .amount {\n" +
                "      font-size: 28px;\n" +
                "      font-weight: 900;\n" +
                "      color: #22C55E;\n" +
                "    }\n" +
                "    .order-id { font-size: 12px; color: #94A3B8; margin-top: 4px; }\n" +
                "    .btn-pay {\n" +
                "      background: linear-gradient(135deg, #8B5CF6, #6D28D9);\n" +
                "      color: white;\n" +
                "      border: none;\n" +
                "      border-radius: 10px;\n" +
                "      padding: 14px 28px;\n" +
                "      font-size: 15px;\n" +
                "      font-weight: 700;\n" +
                "      width: 100%;\n" +
                "      cursor: pointer;\n" +
                "      transition: transform 0.2s, box-shadow 0.2s;\n" +
                "    }\n" +
                "    .btn-pay:hover {\n" +
                "      transform: translateY(-2px);\n" +
                "      box-shadow: 0 8px 20px rgba(139,92,246,0.4);\n" +
                "    }\n" +
                "    .btn-sim {\n" +
                "      background: transparent;\n" +
                "      border: 1px solid rgba(255,255,255,0.15);\n" +
                "      color: #CBD5E1;\n" +
                "      border-radius: 8px;\n" +
                "      padding: 10px 16px;\n" +
                "      font-size: 12px;\n" +
                "      width: 100%;\n" +
                "      margin-top: 10px;\n" +
                "      cursor: pointer;\n" +
                "    }\n" +
                "    .btn-sim:hover { background: rgba(255,255,255,0.06); color: white; }\n" +
                "    .status-msg { margin-top: 18px; font-size: 13px; font-weight: 600; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"card\" id=\"status-card\">\n" +
                "    <div class=\"badge\">⚡ RAZORPAY REALTIME CHECKOUT</div>\n" +
                "    <h1>CineFlow Entertainment</h1>\n" +
                "    <p>Complete payment for <strong>" + safeTitle + "</strong></p>\n" +
                "    <div class=\"amount-box\">\n" +
                "      <div class=\"amount\">₹ " + order.getAmountRupees().toPlainString() + "</div>\n" +
                "      <div class=\"order-id\">Order Ref: <code>" + order.getOrderId() + "</code></div>\n" +
                "    </div>\n" +
                "    <button class=\"btn-pay\" id=\"pay-btn\" onclick=\"openRazorpay()\">Open Razorpay Gateway Modal</button>\n" +
                "    <button class=\"btn-sim\" onclick=\"simulateDirect()\">⚡ Quick Test Mode (Simulate Card/UPI Approval)</button>\n" +
                "    <div id=\"status-msg\" class=\"status-msg\" style=\"color:#A1A1AA;\">Opening checkout popup automatically...</div>\n" +
                "  </div>\n" +
                "\n" +
                "  <script>\n" +
                "    var options = {\n" +
                "      \"key\": \"" + keyId + "\",\n" +
                "      \"amount\": \"" + order.getAmountPaise() + "\",\n" +
                "      \"currency\": \"INR\",\n" +
                "      \"name\": \"CineFlow Cinemas\",\n" +
                "      \"description\": \"" + safeTitle + "\",\n" +
                "      \"order_id\": \"" + order.getOrderId() + "\",\n" +
                "      \"prefill\": {\n" +
                "        \"name\": \"" + customerName + "\",\n" +
                "        \"email\": \"" + customerEmail + "\",\n" +
                "        \"contact\": \"" + customerPhone + "\"\n" +
                "      },\n" +
                "      \"theme\": {\n" +
                "        \"color\": \"#8B5CF6\"\n" +
                "      },\n" +
                "      \"handler\": function(response){\n" +
                "        document.getElementById('status-msg').innerHTML = 'Verifying cryptographic payment signature...';\n" +
                "        document.getElementById('status-msg').style.color = '#00C2FF';\n" +
                "        fetch('/payment-callback', {\n" +
                "          method: 'POST',\n" +
                "          headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify(response)\n" +
                "        }).then(res => res.json()).then(data => {\n" +
                "          document.getElementById('status-card').innerHTML = \n" +
                "            '<h2 style=\"color:#22C55E; margin-bottom:8px;\">Payment Successful! 🎉</h2>' +\n" +
                "            '<p style=\"color:#CBD5E1;\">Payment ID: <code>' + response.razorpay_payment_id + '</code></p>' +\n" +
                "            '<p style=\"color:#94A3B8; font-size:13px;\">Booking verified! Returning to CineFlow desktop application...</p>';\n" +
                "          setTimeout(function(){ window.close(); }, 3500);\n" +
                "        }).catch(err => {\n" +
                "          document.getElementById('status-msg').innerText = 'Callback error: ' + err.message;\n" +
                "        });\n" +
                "      },\n" +
                "      \"modal\": {\n" +
                "        \"ondismiss\": function(){\n" +
                "          document.getElementById('status-msg').innerText = 'Payment window dismissed. Click button above to retry.';\n" +
                "          document.getElementById('status-msg').style.color = '#F59E0B';\n" +
                "          fetch('/payment-cancelled', { method: 'POST' });\n" +
                "        }\n" +
                "      }\n" +
                "    };\n" +
                "\n" +
                "    var rzp1 = null;\n" +
                "    function openRazorpay() {\n" +
                "      try {\n" +
                "        if (!rzp1) rzp1 = new Razorpay(options);\n" +
                "        rzp1.open();\n" +
                "      } catch(e) {\n" +
                "        document.getElementById('status-msg').innerText = 'Could not load Razorpay SDK: ' + e.message;\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function simulateDirect() {\n" +
                "      var pId = 'pay_sim_' + Math.random().toString(36).substring(2, 12);\n" +
                "      var payload = {\n" +
                "        \"razorpay_payment_id\": pId,\n" +
                "        \"razorpay_order_id\": \"" + order.getOrderId() + "\",\n" +
                "        \"razorpay_signature\": \"simulated_test\"\n" +
                "      };\n" +
                "      fetch('/payment-callback', {\n" +
                "        method: 'POST',\n" +
                "        headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify(payload)\n" +
                "      }).then(res => res.json()).then(data => {\n" +
                "        document.getElementById('status-card').innerHTML = \n" +
                "          '<h2 style=\"color:#22C55E;\">Simulated Payment Approved!</h2>' +\n" +
                "          '<p>Payment ID: <code>' + pId + '</code></p>' +\n" +
                "          '<p style=\"color:#94A3B8;\">Returning to CineFlow desktop application...</p>';\n" +
                "        setTimeout(function(){ window.close(); }, 2500);\n" +
                "      });\n" +
                "    }\n" +
                "\n" +
                "    window.onload = function() {\n" +
                "      setTimeout(openRazorpay, 600);\n" +
                "    };\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }

    // ─────────────────────────────────────────────────────────────────
    // UTILITY HELPERS
    // ─────────────────────────────────────────────────────────────────
    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);

        Pattern pNum = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)");
        Matcher mNum = pNum.matcher(json);
        if (mNum.find()) return mNum.group(1);

        return null;
    }

    private static String readBody(InputStream is) throws IOException {
        try (is) {
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
