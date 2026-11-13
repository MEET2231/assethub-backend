package com.assethub.service;

import com.assethub.exception.BadRequestException;
import com.assethub.exception.ConflictException;
import com.assethub.exception.ForbiddenException;
import com.assethub.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BookingService {

    private final JdbcTemplate jdbcTemplate;
    private final HelperService helperService;

    private static final Set<String> MANAGER_ROLES = Set.of("admin", "asset_manager");

    public BookingService(JdbcTemplate jdbcTemplate, HelperService helperService) {
        this.jdbcTemplate = jdbcTemplate;
        this.helperService = helperService;
    }

    private void checkManager(String role) {
        if (!MANAGER_ROLES.contains(role)) {
            throw new ForbiddenException("Only admins or asset managers can perform this action");
        }
    }

    private Timestamp parseTimestamp(Object obj) {
        return HelperService.parseTimestamp(obj);
    }

    public List<Map<String, Object>> getBookings(Long userId, String role, String assetId, String mine, String includeCancelled) {
        boolean isManager = MANAGER_ROLES.contains(role);
        StringBuilder sql = new StringBuilder(
                """
                SELECT b.*, a.asset_tag, a.name AS asset_name, u.name AS booked_by_name,
                  CASE WHEN b.status = 'cancelled' THEN 'cancelled'
                       WHEN b.status = 'rejected' THEN 'rejected'
                       WHEN b.status = 'pending' THEN 'pending'
                       WHEN now() < b.start_time THEN 'upcoming'
                       WHEN now() BETWEEN b.start_time AND b.end_time THEN 'ongoing'
                       ELSE 'completed' END AS live_status
                FROM bookings b
                JOIN assets a ON a.id = b.asset_id
                JOIN users u ON u.id = b.booked_by
                WHERE 1=1
                """
        );
        List<Object> params = new ArrayList<>();

        if (assetId != null && !assetId.isBlank()) {
            sql.append(" AND b.asset_id = ? AND b.status = 'approved'");
            params.add(Long.parseLong(assetId.trim()));
        } else if ("true".equalsIgnoreCase(mine)) {
            sql.append(" AND b.booked_by = ?");
            params.add(userId);
            if (!"true".equalsIgnoreCase(includeCancelled)) {
                sql.append(" AND b.status != 'cancelled'");
            }
        } else if (isManager) {
            if (!"true".equalsIgnoreCase(includeCancelled)) {
                sql.append(" AND b.status != 'cancelled'");
            }
        } else {
            sql.append(" AND b.status = 'approved'");
        }

        sql.append(" ORDER BY b.start_time");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> createBooking(Map<String, Object> req, Long userId) {
        Object assetIdObj = req.get("asset_id");
        Object startObj = req.get("start_time");
        Object endObj = req.get("end_time");
        String purpose = (String) req.get("purpose");

        if (assetIdObj == null || startObj == null || endObj == null) {
            throw new BadRequestException("asset_id, start_time, end_time required");
        }

        Long assetId;
        try {
            assetId = HelperService.toLong(assetIdObj);
        } catch (Exception e) {
            throw new BadRequestException("Invalid asset_id");
        }

        Timestamp startTime;
        Timestamp endTime;
        try {
            startTime = parseTimestamp(startObj);
            endTime = parseTimestamp(endObj);
        } catch (Exception e) {
            throw new BadRequestException("Invalid start_time or end_time format: " + e.getMessage());
        }

        if (startTime == null || endTime == null || !endTime.after(startTime)) {
            throw new BadRequestException("end_time must be after start_time");
        }

        List<Map<String, Object>> assets = jdbcTemplate.queryForList(
                "SELECT * FROM assets WHERE id = ? AND is_bookable = true", assetId
        );
        if (assets.isEmpty()) {
            throw new NotFoundException("Bookable resource not found");
        }
        Map<String, Object> asset = assets.get(0);

        // Overlap validation: clash with approved bookings
        List<Map<String, Object>> clashes = jdbcTemplate.queryForList(
                """
                SELECT b.*, u.name AS booked_by_name
                FROM bookings b
                JOIN users u ON u.id = b.booked_by
                WHERE b.asset_id = ? AND b.status = 'approved'
                  AND b.start_time < ? AND b.end_time > ?
                LIMIT 1
                """,
                assetId, endTime, startTime
        );

        if (!clashes.isEmpty()) {
            Map<String, Object> clash = clashes.get(0);
            String bookedByName = (String) clash.get("booked_by_name");
            throw new ConflictException("Overlaps an existing booking by " + bookedByName);
        }

        jdbcTemplate.update(
                "INSERT INTO bookings (asset_id, booked_by, start_time, end_time, purpose, status) VALUES (?, ?, ?, ?, ?, 'pending')",
                assetId, userId, startTime, endTime, purpose
        );

        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT * FROM bookings WHERE asset_id = ? AND booked_by = ? ORDER BY id DESC LIMIT 1",
                assetId, userId
        );

        String assetName = (String) asset.get("name");
        helperService.logActivity(userId, "booking.created", "asset", assetId, assetName + " (pending approval)");
        helperService.notify(userId, "booking_requested", "Booking requested (pending approval): " + assetName);

        return created;
    }

    public Map<String, Object> cancelBooking(Long bookingId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM bookings WHERE id = ? AND booked_by = ? AND status IN ('pending', 'approved')",
                bookingId, userId
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Booking not found or cannot be cancelled");
        }

        jdbcTemplate.update("UPDATE bookings SET status = 'cancelled' WHERE id = ?", bookingId);
        helperService.notify(userId, "booking_cancelled", "Your booking was cancelled");
        helperService.logActivity(userId, "booking.cancelled", "booking", bookingId, null);

        return jdbcTemplate.queryForMap("SELECT * FROM bookings WHERE id = ?", bookingId);
    }

    public Map<String, Object> approveBooking(Long bookingId, Long userId, String userRole) {
        checkManager(userRole);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM bookings WHERE id = ?", bookingId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Booking not found");
        }
        Map<String, Object> booking = rows.get(0);

        String status = (String) booking.get("status");
        if (!"pending".equalsIgnoreCase(status)) {
            throw new BadRequestException("Only pending bookings can be approved");
        }

        Long assetId = ((Number) booking.get("asset_id")).longValue();
        Timestamp startTime = parseTimestamp(booking.get("start_time"));
        Timestamp endTime = parseTimestamp(booking.get("end_time"));
        Long bookedBy = ((Number) booking.get("booked_by")).longValue();

        // Double check overlap on approval
        List<Map<String, Object>> clashes = jdbcTemplate.queryForList(
                """
                SELECT b.*, u.name AS booked_by_name
                FROM bookings b
                JOIN users u ON u.id = b.booked_by
                WHERE b.asset_id = ? AND b.status = 'approved' AND b.id != ?
                  AND b.start_time < ? AND b.end_time > ?
                LIMIT 1
                """,
                assetId, bookingId, endTime, startTime
        );

        if (!clashes.isEmpty()) {
            Map<String, Object> clash = clashes.get(0);
            String bookedByName = (String) clash.get("booked_by_name");
            throw new ConflictException("Cannot approve. Overlaps with an approved booking by " + bookedByName);
        }

        jdbcTemplate.update("UPDATE bookings SET status = 'approved' WHERE id = ?", bookingId);

        List<String> assetNames = jdbcTemplate.query(
                "SELECT name FROM assets WHERE id = ?", (rs, rowNum) -> rs.getString("name"), assetId
        );
        String assetName = !assetNames.isEmpty() ? assetNames.get(0) : String.valueOf(assetId);

        helperService.logActivity(userId, "booking.approved", "booking", bookingId, "Approved booking for " + assetName);
        helperService.notify(bookedBy, "booking_confirmed", "Your booking for " + assetName + " was approved");

        return jdbcTemplate.queryForMap("SELECT * FROM bookings WHERE id = ?", bookingId);
    }

    public Map<String, Object> rejectBooking(Long bookingId, Long userId, String userRole) {
        checkManager(userRole);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM bookings WHERE id = ?", bookingId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Booking not found");
        }
        Map<String, Object> booking = rows.get(0);

        String status = (String) booking.get("status");
        if (!"pending".equalsIgnoreCase(status)) {
            throw new BadRequestException("Only pending bookings can be rejected");
        }

        Long assetId = ((Number) booking.get("asset_id")).longValue();
        Long bookedBy = ((Number) booking.get("booked_by")).longValue();

        jdbcTemplate.update("UPDATE bookings SET status = 'rejected' WHERE id = ?", bookingId);

        List<String> assetNames = jdbcTemplate.query(
                "SELECT name FROM assets WHERE id = ?", (rs, rowNum) -> rs.getString("name"), assetId
        );
        String assetName = !assetNames.isEmpty() ? assetNames.get(0) : String.valueOf(assetId);

        helperService.logActivity(userId, "booking.rejected", "booking", bookingId, "Rejected booking for " + assetName);
        helperService.notify(bookedBy, "booking_rejected", "Your booking request for " + assetName + " was rejected");

        return jdbcTemplate.queryForMap("SELECT * FROM bookings WHERE id = ?", bookingId);
    }
}
