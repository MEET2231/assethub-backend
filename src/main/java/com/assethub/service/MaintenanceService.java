package com.assethub.service;

import com.assethub.exception.BadRequestException;
import com.assethub.exception.ConflictException;
import com.assethub.exception.ForbiddenException;
import com.assethub.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MaintenanceService {

    private final JdbcTemplate jdbcTemplate;
    private final HelperService helperService;

    private static final Set<String> MANAGER_ROLES = Set.of("admin", "asset_manager");

    private record Transition(List<String> from, String to, Set<String> roles) {}

    private static final Map<String, Transition> TRANSITIONS = Map.of(
            "approve", new Transition(List.of("pending"), "approved", MANAGER_ROLES),
            "reject",  new Transition(List.of("pending"), "rejected", MANAGER_ROLES),
            "assign",  new Transition(List.of("approved"), "assigned", MANAGER_ROLES),
            "start",   new Transition(List.of("assigned"), "in_progress", MANAGER_ROLES),
            "resolve", new Transition(List.of("in_progress", "assigned"), "resolved", MANAGER_ROLES)
    );

    public MaintenanceService(JdbcTemplate jdbcTemplate, HelperService helperService) {
        this.jdbcTemplate = jdbcTemplate;
        this.helperService = helperService;
    }

    public List<Map<String, Object>> getRequests(String status) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT m.*, a.asset_tag, a.name AS asset_name, u.name AS raised_by_name, db.name AS decided_by_name
                FROM maintenance_requests m
                JOIN assets a ON a.id = m.asset_id
                JOIN users u ON u.id = m.raised_by
                LEFT JOIN users db ON db.id = m.decided_by
                """
        );
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" WHERE m.status = ?");
            params.add(status.trim());
        }

        sql.append(" ORDER BY m.created_at DESC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> createRequest(Map<String, Object> req, Long userId) {
        Object assetIdObj = req.get("asset_id");
        String issue = (String) req.get("issue");
        String priority = req.get("priority") != null ? (String) req.get("priority") : "medium";
        String photoUrl = (String) req.get("photo_url");

        if (assetIdObj == null || issue == null || issue.isBlank()) {
            throw new BadRequestException("asset_id and issue are required");
        }
        Long assetId = ((Number) assetIdObj).longValue();

        jdbcTemplate.update(
                """
                INSERT INTO maintenance_requests (asset_id, raised_by, issue, priority, photo_url, status)
                VALUES (?, ?, ?, ?, ?, 'pending')
                """,
                assetId, userId, issue.trim(), priority, photoUrl
        );

        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT * FROM maintenance_requests WHERE asset_id = ? AND raised_by = ? ORDER BY id DESC LIMIT 1",
                assetId, userId
        );

        helperService.logActivity(userId, "maintenance.raised", "asset", assetId, issue.trim());
        return created;
    }

    @Transactional
    public Map<String, Object> transitionRequest(Long id, Map<String, Object> req, Long userId, String userRole) {
        String action = (String) req.get("action");
        String technician = (String) req.get("technician");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM maintenance_requests WHERE id = ?", id
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Request not found");
        }
        Map<String, Object> m = rows.get(0);

        Transition t = action != null ? TRANSITIONS.get(action.toLowerCase()) : null;
        if (t == null) {
            throw new BadRequestException("Unknown action '" + action + "'");
        }
        if (!t.roles().contains(userRole)) {
            throw new ForbiddenException("Only an Asset Manager can do this");
        }

        String currentStatus = (String) m.get("status");
        if (!t.from().contains(currentStatus)) {
            throw new ConflictException("Cannot " + action + " a request that is " + currentStatus);
        }

        Long assetId = ((Number) m.get("asset_id")).longValue();
        Long raisedBy = ((Number) m.get("raised_by")).longValue();

        String existingTechnician = (String) m.get("technician");
        String techToSet = (technician != null && !technician.isBlank()) ? technician : existingTechnician;

        if ("approved".equals(t.to()) || "rejected".equals(t.to())) {
            jdbcTemplate.update(
                    """
                    UPDATE maintenance_requests SET status = ?, technician = ?, decided_by = ?, decided_at = now()
                    WHERE id = ?
                    """,
                    t.to(), techToSet, userId, id
            );
        } else if ("resolved".equals(t.to())) {
            jdbcTemplate.update(
                    """
                    UPDATE maintenance_requests SET status = ?, technician = ?, resolved_at = now()
                    WHERE id = ?
                    """,
                    t.to(), techToSet, id
            );
        } else {
            jdbcTemplate.update(
                    """
                    UPDATE maintenance_requests SET status = ?, technician = ?
                    WHERE id = ?
                    """,
                    t.to(), techToSet, id
            );
        }

        // Asset status side-effects
        if ("approved".equals(t.to())) {
            jdbcTemplate.update("UPDATE assets SET status = 'under_maintenance' WHERE id = ?", assetId);
        }
        if ("resolved".equals(t.to())) {
            List<Long> activeAllocs = jdbcTemplate.query(
                    "SELECT id FROM allocations WHERE asset_id = ? AND status = 'active'",
                    (rs, rowNum) -> rs.getLong("id"),
                    assetId
            );
            String nextAssetStatus = activeAllocs.isEmpty() ? "available" : "allocated";
            jdbcTemplate.update("UPDATE assets SET status = ? WHERE id = ?", nextAssetStatus, assetId);
        }

        helperService.notify(raisedBy, "maintenance_" + t.to(), "Your maintenance request #" + id + " is now " + t.to().replace('_', ' '));
        helperService.logActivity(userId, "maintenance." + t.to(), "asset", assetId, technician);

        return jdbcTemplate.queryForMap("SELECT * FROM maintenance_requests WHERE id = ?", id);
    }
}
