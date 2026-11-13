package com.assethub.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class HelperService {

    private final JdbcTemplate jdbcTemplate;

    public HelperService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.longValue();
        String str = obj.toString().trim();
        if (str.isEmpty()) return null;
        return Long.parseLong(str);
    }

    public static Timestamp parseTimestamp(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Timestamp ts) return ts;
        if (obj instanceof OffsetDateTime odt) return Timestamp.from(odt.toInstant());
        if (obj instanceof Instant inst) return Timestamp.from(inst);
        if (obj instanceof LocalDateTime ldt) return Timestamp.valueOf(ldt);
        if (obj instanceof java.util.Date d) return new Timestamp(d.getTime());

        String str = obj.toString().trim();
        if (str.isEmpty()) return null;

        // Try standard ISO Instant
        try {
            return Timestamp.from(Instant.parse(str));
        } catch (Exception ignored) {}

        // Try OffsetDateTime
        try {
            return Timestamp.from(OffsetDateTime.parse(str).toInstant());
        } catch (Exception ignored) {}

        // Normalize string for LocalDateTime:
        String normalized = str;
        if (normalized.endsWith("Z") || normalized.endsWith("z")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains(" ") && !normalized.contains("T")) {
            normalized = normalized.replace(" ", "T");
        }

        // Support yyyy-MM-dd
        if (normalized.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            normalized += "T00:00:00";
        }
        // Support yyyy-MM-ddTHH:mm (HTML datetime-local format)
        else if (normalized.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$")) {
            normalized += ":00";
        }

        try {
            LocalDateTime ldt = LocalDateTime.parse(normalized);
            return Timestamp.valueOf(ldt);
        } catch (Exception ignored) {}

        // Fallback for SQL format yyyy-MM-dd HH:mm:ss
        String sqlStr = normalized.replace("T", " ");
        if (sqlStr.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
            sqlStr += ":00";
        }
        return Timestamp.valueOf(sqlStr);
    }

    public static Date parseDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Date d) return d;
        if (obj instanceof java.util.Date d) return new Date(d.getTime());
        String str = obj.toString().trim();
        if (str.isEmpty()) return null;
        if (str.contains("T")) {
            str = str.substring(0, str.indexOf("T"));
        } else if (str.contains(" ")) {
            str = str.substring(0, str.indexOf(" "));
        }
        return Date.valueOf(str);
    }

    public void notify(Long userId, String type, String message) {
        if (userId == null) return;
        jdbcTemplate.update(
                "INSERT INTO notifications (user_id, type, message) VALUES (?, ?, ?)",
                userId, type, message
        );
    }

    public void logActivity(Long userId, String action, String entityType, Long entityId, String details) {
        jdbcTemplate.update(
                "INSERT INTO activity_logs (user_id, action, entity_type, entity_id, details) VALUES (?, ?, ?, ?, ?)",
                userId, action, entityType, entityId, details
        );
    }

    public synchronized String nextAssetTag() {
        List<String> tags = jdbcTemplate.query(
                "SELECT asset_tag FROM assets ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString("asset_tag")
        );

        int last = 0;
        if (!tags.isEmpty() && tags.get(0) != null) {
            String tag = tags.get(0);
            try {
                String[] parts = tag.split("-");
                if (parts.length > 1) {
                    last = Integer.parseInt(parts[1]);
                }
            } catch (Exception ignored) {
            }
        }

        return String.format("AF-%04d", last + 1);
    }
}
