package com.assethub.service;

import com.assethub.exception.BadRequestException;
import com.assethub.exception.ConflictException;
import com.assethub.exception.ForbiddenException;
import com.assethub.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TechnicianService {

    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> MANAGER_ROLES = Set.of("admin", "asset_manager");

    public TechnicianService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private void checkManager(String role) {
        if (!MANAGER_ROLES.contains(role)) {
            throw new ForbiddenException("Requires role: admin or asset_manager");
        }
    }

    public List<Map<String, Object>> getTechnicians() {
        return jdbcTemplate.queryForList(
                """
                SELECT t.*,
                       COALESCE(SUM(CASE WHEN m.status IN ('assigned', 'in_progress') THEN 1 ELSE 0 END), 0) AS active_requests_count
                FROM technicians t
                LEFT JOIN maintenance_requests m ON (m.technician_id = t.id OR m.technician = t.name)
                GROUP BY t.id, t.name, t.email, t.phone, t.specialty, t.status, t.created_at
                ORDER BY t.name
                """
        );
    }

    public Map<String, Object> createTechnician(Map<String, Object> req, String userRole) {
        checkManager(userRole);

        String name = (String) req.get("name");
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Name is required");
        }

        String email = (String) req.get("email");
        String lowerEmail = (email != null && !email.isBlank()) ? email.trim().toLowerCase() : null;

        if (lowerEmail != null) {
            List<Long> existing = jdbcTemplate.query(
                    "SELECT id FROM technicians WHERE LOWER(email) = ?",
                    (rs, rowNum) -> rs.getLong("id"),
                    lowerEmail
            );
            if (!existing.isEmpty()) {
                throw new ConflictException("A technician with this email already exists");
            }
        }

        String phone = (String) req.get("phone");
        String specialty = (String) req.get("specialty");

        jdbcTemplate.update(
                "INSERT INTO technicians (name, email, phone, specialty) VALUES (?, ?, ?, ?)",
                name.trim(), lowerEmail, phone, specialty
        );

        return jdbcTemplate.queryForMap(
                "SELECT * FROM technicians WHERE name = ? ORDER BY id DESC LIMIT 1",
                name.trim()
        );
    }

    public Map<String, Object> updateTechnician(Long id, Map<String, Object> req, String userRole) {
        checkManager(userRole);

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM technicians WHERE id = ?", id);
        if (existing.isEmpty()) {
            throw new NotFoundException("Technician not found");
        }
        Map<String, Object> current = existing.get(0);

        String status = (String) req.get("status");
        if (status != null && !status.isBlank() && !"active".equalsIgnoreCase(status) && !"inactive".equalsIgnoreCase(status)) {
            throw new BadRequestException("Invalid status value. Must be active or inactive");
        }

        String email = (String) req.get("email");
        String lowerEmail = (email != null && !email.isBlank()) ? email.trim().toLowerCase() : null;
        if (lowerEmail != null) {
            List<Long> duplicate = jdbcTemplate.query(
                    "SELECT id FROM technicians WHERE LOWER(email) = ? AND id <> ?",
                    (rs, rowNum) -> rs.getLong("id"),
                    lowerEmail, id
            );
            if (!duplicate.isEmpty()) {
                throw new ConflictException("Another technician with this email already exists");
            }
        }

        String name = req.containsKey("name") && req.get("name") != null ? (String) req.get("name") : (String) current.get("name");
        String phone = req.containsKey("phone") ? (String) req.get("phone") : (String) current.get("phone");
        String specialty = req.containsKey("specialty") ? (String) req.get("specialty") : (String) current.get("specialty");
        String targetStatus = status != null ? status : (String) current.get("status");
        String targetEmail = req.containsKey("email") ? lowerEmail : (String) current.get("email");

        jdbcTemplate.update(
                """
                UPDATE technicians SET name = ?, email = ?, phone = ?, specialty = ?, status = ?
                WHERE id = ?
                """,
                name, targetEmail, phone, specialty, targetStatus, id
        );

        return jdbcTemplate.queryForMap("SELECT * FROM technicians WHERE id = ?", id);
    }
}
