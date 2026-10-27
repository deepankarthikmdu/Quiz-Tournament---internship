package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.dto.PaymentResponse;
import org.example.dto.QuizResponse;
import org.example.model.Payment;
import org.example.model.Quiz;
import org.example.service.PaymentService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/payments")
public class PaymentApiServlet extends HttpServlet {
    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUser(req, res);
        if (userId == null) return;

        StartPaymentRequest body = readRequest(req, res);
        if (body == null) return;
        try {
            Payment payment = paymentService.startForRegistration(userId, body.paymentId(), body.registerId(), body.roundParticipantId());
            JsonUtil.sendJson(res, 201, Map.of(
                    "payment", toResponse(payment),
                    "message", "Payment successfull"));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUser(req, res);
        if (userId == null) return;
        String paymentId = req.getParameter("paymentId");
        if (paymentId == null) paymentId = req.getParameter("id");

        try {
            if (paymentId == null) {
                JsonUtil.sendJson(res, 200, toResponseList(paymentService.findAllForOwner(userId)));
                return;
            }
            Payment payment = paymentService.findByIdForUser(paymentId.trim(), userId);
            if (payment == null) JsonUtil.sendError(res, 404, "Payment not found");
            else JsonUtil.sendJson(res, 200, toResponse(payment));
        } catch (SQLException e) {
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    // Payment status is changed only by a verified payment-provider callback, which is not configured yet.
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        JsonUtil.sendError(res, 405, "Payment status cannot be changed by the client");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        JsonUtil.sendError(res, 405, "Payments cannot be deleted through this endpoint");
    }

    private StartPaymentRequest readRequest(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            StartPaymentRequest body = JsonUtil.fromJson(reader, StartPaymentRequest.class);
            if (body == null || body.registerId() == null || body.registerId().isBlank()) {
                JsonUtil.sendError(res, 400, "registerId is required");
                return null;
            }
            return body;

        } catch (JsonParseException e) {
            JsonUtil.sendError(res, 400, "Invalid or missing JSON body");
            return null;
        }
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getPaymentId(), p.getUserId(), p.getAmount(), p.getDescription(),
                p.getPaymentDate(), p.getStatus(), p.getPaymentGateway());
    }

    private List<PaymentResponse> toResponseList(List<Payment> payments) {
        return payments.stream().map(this::toResponse).toList();
    }

    private String authenticatedUser(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object userId = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if (!(userId instanceof String id) || id.isBlank()) {
            JsonUtil.sendError(res, 401, "login required");
            return null;
        }
        return id;
    }

    private void logDatabaseError(SQLException e) {
        getServletContext().log("Payment database operation failed (SQLState=" + e.getSQLState()
                + ", vendorCode=" + e.getErrorCode() + ")", e);
    }

    private record StartPaymentRequest(String paymentId, String registerId, String roundParticipantId) { }
}
