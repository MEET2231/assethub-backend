package com.assethub.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HelperService {

    private final JdbcTemplate jdbcTemplate;

    public HelperService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
