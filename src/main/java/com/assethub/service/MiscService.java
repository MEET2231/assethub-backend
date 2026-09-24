package com.assethub.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

@Service
public class MiscService {

    private final JdbcTemplate jdbcTemplate;
    private final HelperService helperService;

    private static final Set<String> MANAGER_ROLES = Set.of("admin", "asset_manager");

    public MiscService(JdbcTemplate jdbcTemplate, HelperService helperService) {
        this.jdbcTemplate = jdbcTemplate;
        this.helperService = helperService;
    }

    public Map<String, Object> getDashboard(Long userId, String role) {
        boolean isManager = MANAGER_ROLES.contains(role);

        Map<String, Object> kpis = jdbcTemplate.queryForMap(
                """
                SELECT
                  (SELECT COUNT(*) FROM assets WHERE status = 'available') AS assets_available,
                  (SELECT COUNT(*) FROM assets WHERE status = 'allocated') AS assets_allocated,
                  (SELECT COUNT(*) FROM maintenance_requests WHERE status IN ('approved','assigned','in_progress')) AS maintenance_active,
                  (SELECT COUNT(*) FROM bookings WHERE status != 'cancelled' AND now() BETWEEN start_time AND end_time) AS active_bookings,
                  (SELECT COUNT(*) FROM transfer_requests WHERE status = 'pending') AS pending_transfers,
                  (SELECT COUNT(*) FROM bookings WHERE status = 'pending') AS pending_bookings_count,
                  (SELECT COUNT(*) FROM maintenance_requests WHERE status IN ('pending','approved','assigned','in_progress')) AS pending_maintenance_count,
                  (SELECT COUNT(*) FROM allocations WHERE status = 'active' AND expected_return_date >= CURRENT_DATE AND expected_return_date <= CURRENT_DATE + 7) AS upcoming_returns,
                  (SELECT COUNT(*) FROM allocations WHERE status = 'active' AND expected_return_date < CURRENT_DATE) AS overdue_returns
                """
        );

        List<Map<String, Object>> overdue = jdbcTemplate.queryForList(
                """
                SELECT al.id, al.expected_return_date, a.asset_tag, a.name AS asset_name, u.name AS employee_name
                FROM allocations al
                JOIN assets a ON a.id = al.asset_id
                LEFT JOIN users u ON u.id = al.employee_id
                WHERE al.status = 'active' AND al.expected_return_date < CURRENT_DATE
                ORDER BY al.expected_return_date LIMIT 10
                """
        );

        List<Map<String, Object>> upcoming = jdbcTemplate.queryForList(
                """
                SELECT al.id, al.expected_return_date, a.asset_tag, a.name AS asset_name, u.name AS employee_name
                FROM allocations al
                JOIN assets a ON a.id = al.asset_id
                LEFT JOIN users u ON u.id = al.employee_id
                WHERE al.status = 'active' AND al.expected_return_date >= CURRENT_DATE AND al.expected_return_date <= CURRENT_DATE + 7
                ORDER BY al.expected_return_date LIMIT 10
                """
        );

        List<Map<String, Object>> pendingBookings;
        if (isManager) {
            pendingBookings = jdbcTemplate.queryForList(
                    """
                    SELECT b.id, b.start_time, b.end_time, b.status, b.purpose, a.asset_tag, a.name AS asset_name, u.name AS booked_by_name
                    FROM bookings b
                    JOIN assets a ON a.id = b.asset_id
                    JOIN users u ON u.id = b.booked_by
                    WHERE b.status = 'pending'
                    ORDER BY b.created_at DESC LIMIT 10
                    """
            );
        } else {
            pendingBookings = jdbcTemplate.queryForList(
                    """
                    SELECT b.id, b.start_time, b.end_time, b.status, b.purpose, a.asset_tag, a.name AS asset_name, u.name AS booked_by_name
                    FROM bookings b
                    JOIN assets a ON a.id = b.asset_id
                    JOIN users u ON u.id = b.booked_by
                    WHERE b.booked_by = ? AND b.status != 'cancelled'
                    ORDER BY b.start_time LIMIT 10
                    """, userId
            );
        }

        List<Map<String, Object>> myAssets = jdbcTemplate.queryForList(
                """
                SELECT al.id, al.expected_return_date, a.asset_tag, a.name AS asset_name, c.name AS category_name
                FROM allocations al
                JOIN assets a ON a.id = al.asset_id
                LEFT JOIN categories c ON c.id = a.category_id
                WHERE al.employee_id = ? AND al.status = 'active'
                ORDER BY al.expected_return_date IS NULL, al.expected_return_date ASC LIMIT 10
                """, userId
        );

        List<Map<String, Object>> recentActivity = jdbcTemplate.queryForList(
                """
                SELECT l.*, u.name AS user_name
                FROM activity_logs l
                LEFT JOIN users u ON u.id = l.user_id
                ORDER BY l.created_at DESC LIMIT 10
                """
        );

        List<Map<String, Object>> bookingTimeline = jdbcTemplate.queryForList(
                """
                SELECT b.id, b.start_time, b.end_time, b.purpose,
                       a.name AS asset_name, a.asset_tag,
                       u.name AS booked_by_name
                FROM bookings b
                JOIN assets a ON a.id = b.asset_id
                LEFT JOIN users u ON u.id = b.booked_by
                WHERE b.status = 'approved'
                  AND b.start_time < CURRENT_DATE + 1
                  AND b.end_time >= CURRENT_DATE
                ORDER BY b.start_time ASC
                """
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("kpis", kpis);
        response.put("pending_bookings", pendingBookings);
        response.put("my_assets", myAssets);
        response.put("recent_activity", recentActivity);
        response.put("booking_timeline", bookingTimeline);
        response.put("overdue_returns", overdue);
        response.put("upcoming_returns", upcoming);
        return response;
    }

    public List<Map<String, Object>> getNotifications(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50", userId
        );
    }

    public Map<String, Object> markAllNotificationsRead(Long userId) {
        jdbcTemplate.update("UPDATE notifications SET read = true WHERE user_id = ?", userId);
        return Map.of("ok", true);
    }

    public Map<String, Object> markNotificationRead(Long id, Long userId) {
        jdbcTemplate.update("UPDATE notifications SET read = true WHERE id = ? AND user_id = ?", id, userId);
        return Map.of("ok", true);
    }

    public Map<String, Object> checkOverdueNotifications() {
        List<Map<String, Object>> overdue = jdbcTemplate.queryForList(
                """
                SELECT al.id, al.employee_id, al.expected_return_date, a.asset_tag, a.name
                FROM allocations al
                JOIN assets a ON a.id = al.asset_id
                WHERE al.status = 'active' AND al.expected_return_date < CURRENT_DATE AND al.employee_id IS NOT NULL
                """
        );

        int sent = 0;
        for (Map<String, Object> r : overdue) {
            Long employeeId = ((Number) r.get("employee_id")).longValue();
            String tag = (String) r.get("asset_tag");
            String name = (String) r.get("name");
            Object returnDate = r.get("expected_return_date");

            List<Long> existing = jdbcTemplate.query(
                    """
                    SELECT id FROM notifications
                    WHERE user_id = ? AND type = 'overdue_return'
                      AND message LIKE ? AND CAST(created_at AS DATE) = CURRENT_DATE
                    """,
                    (rs, rowNum) -> rs.getLong("id"),
                    employeeId, "%" + tag + "%"
            );

            if (existing.isEmpty()) {
                helperService.notify(
                        employeeId,
                        "overdue_return",
                        "⚠️ Overdue: " + name + " (" + tag + ") was due back on " + returnDate
                );
                sent++;
            }
        }

        return Map.of("checked", overdue.size(), "sent", sent);
    }

    public Map<String, Object> checkBookingReminders() {
        List<Map<String, Object>> upcoming = jdbcTemplate.queryForList(
                """
                SELECT b.id, b.booked_by, b.start_time, a.name AS asset_name, a.asset_tag
                FROM bookings b
                JOIN assets a ON a.id = b.asset_id
                WHERE b.status = 'approved'
                  AND b.start_time BETWEEN now() AND now() + INTERVAL '1 hour'
                """
        );

        int sent = 0;
        for (Map<String, Object> b : upcoming) {
            Long bookedBy = ((Number) b.get("booked_by")).longValue();
            String tag = (String) b.get("asset_tag");
            String assetName = (String) b.get("asset_name");
            Object startTime = b.get("start_time");

            List<Long> existing = jdbcTemplate.query(
                    """
                    SELECT id FROM notifications
                    WHERE user_id = ? AND type = 'booking_reminder'
                      AND message LIKE ? AND CAST(created_at AS DATE) = CURRENT_DATE
                    """,
                    (rs, rowNum) -> rs.getLong("id"),
                    bookedBy, "%" + tag + "%"
            );

            if (existing.isEmpty()) {
                helperService.notify(
                        bookedBy,
                        "booking_reminder",
                        "🔔 Reminder: Your booking for " + assetName + " (" + tag + ") starts at " + startTime
                );
                sent++;
            }
        }

        return Map.of("checked", upcoming.size(), "sent", sent);
    }

    public List<Map<String, Object>> getActivity() {
        return jdbcTemplate.queryForList(
                """
                SELECT l.*, u.name AS user_name
                FROM activity_logs l
                LEFT JOIN users u ON u.id = l.user_id
                ORDER BY l.created_at DESC LIMIT 100
                """
        );
    }

    public Map<String, Object> getReports() {
        List<Map<String, Object>> byStatus = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*)::int AS count FROM assets GROUP BY status ORDER BY count DESC"
        );

        List<Map<String, Object>> byDept = jdbcTemplate.queryForList(
                """
                SELECT COALESCE(d.name, ud.name, 'Unassigned') AS department, COUNT(*)::int AS count
                FROM allocations al
                LEFT JOIN departments d ON d.id = al.department_id
                LEFT JOIN users u ON u.id = al.employee_id
                LEFT JOIN departments ud ON ud.id = u.department_id
                WHERE al.status = 'active'
                GROUP BY COALESCE(d.name, ud.name, 'Unassigned')
                ORDER BY count DESC
                """
        );

        List<Map<String, Object>> maintFreq = jdbcTemplate.queryForList(
                """
                SELECT a.asset_tag, a.name, COUNT(m.id)::int AS requests
                FROM maintenance_requests m
                JOIN assets a ON a.id = m.asset_id
                GROUP BY a.id, a.asset_tag, a.name
                ORDER BY requests DESC LIMIT 10
                """
        );

        List<Map<String, Object>> mostUsed = jdbcTemplate.queryForList(
                """
                SELECT a.asset_tag, a.name, COUNT(al.id)::int AS allocation_count
                FROM assets a
                LEFT JOIN allocations al ON al.asset_id = a.id
                GROUP BY a.id, a.asset_tag, a.name
                ORDER BY allocation_count DESC LIMIT 10
                """
        );

        List<Map<String, Object>> bookingHeatmap = jdbcTemplate.queryForList(
                """
                SELECT EXTRACT(DOW FROM b.start_time)::int AS day_of_week,
                       EXTRACT(HOUR FROM b.start_time)::int AS hour_of_day,
                       COUNT(*)::int AS booking_count
                FROM bookings b
                WHERE b.status = 'approved' AND b.start_time >= CURRENT_DATE - 45
                GROUP BY EXTRACT(DOW FROM b.start_time)::int, EXTRACT(HOUR FROM b.start_time)::int
                ORDER BY 1, 2
                """
        );

        List<Map<String, Object>> idleAssets = jdbcTemplate.queryForList(
                """
                SELECT a.asset_tag, a.name, c.name AS category_name, COUNT(al.id)::int AS allocation_count
                FROM assets a
                JOIN categories c ON c.id = a.category_id
                LEFT JOIN allocations al ON al.asset_id = a.id
                WHERE a.status NOT IN ('retired', 'disposed', 'lost')
                GROUP BY a.id, a.asset_tag, a.name, c.name
                HAVING COUNT(al.id) = 0
                ORDER BY a.asset_tag LIMIT 10
                """
        );

        List<Map<String, Object>> warrantyExpiring = jdbcTemplate.queryForList(
                """
                SELECT asset_tag, name, warranty_expiry, (warranty_expiry < CURRENT_DATE) AS expired
                FROM assets
                WHERE warranty_expiry IS NOT NULL
                  AND warranty_expiry < CURRENT_DATE + 90
                  AND status NOT IN ('retired', 'disposed', 'lost')
                ORDER BY warranty_expiry LIMIT 10
                """
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("assets_by_status", byStatus);
        response.put("department_allocation", byDept);
        response.put("maintenance_frequency", maintFreq);
        response.put("most_used_assets", mostUsed);
        response.put("bookings_heatmap", bookingHeatmap);
        response.put("idle_assets", idleAssets);
        response.put("warranty_expiring", warrantyExpiring);
        return response;
    }
}
