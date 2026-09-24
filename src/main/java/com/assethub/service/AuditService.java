package com.assethub.service;

import com.assethub.exception.BadRequestException;
import com.assethub.exception.ForbiddenException;
import com.assethub.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.*;

@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;
    private final HelperService helperService;

    private static final Set<String> MANAGER_ROLES = Set.of("admin", "asset_manager");
    private static final Set<String> VALID_RESULTS = Set.of("verified", "missing", "damaged");

    public AuditService(JdbcTemplate jdbcTemplate, HelperService helperService) {
        this.jdbcTemplate = jdbcTemplate;
        this.helperService = helperService;
    }

    private void checkManager(String role) {
        if (!MANAGER_ROLES.contains(role)) {
            throw new ForbiddenException("Requires role: admin or asset_manager");
        }
    }

    public List<Map<String, Object>> getAuditCycles() {
        List<Map<String, Object>> cycles = jdbcTemplate.queryForList(
                """
                SELECT ac.*, d.name AS department_name, u.name AS created_by_name,
                  (SELECT COUNT(*) FROM audit_records ar WHERE ar.cycle_id = ac.id) AS records_count,
                  (SELECT COUNT(*) FROM audit_records ar WHERE ar.cycle_id = ac.id AND ar.result != 'verified') AS discrepancy_count
                FROM audit_cycles ac
                LEFT JOIN departments d ON d.id = ac.scope_department_id
                JOIN users u ON u.id = ac.created_by
                ORDER BY ac.created_at DESC
                """
        );

        for (Map<String, Object> cycle : cycles) {
            Long cycleId = ((Number) cycle.get("id")).longValue();
            List<Map<String, Object>> auditors = jdbcTemplate.queryForList(
                    """
                    SELECT au.id, au.name
                    FROM audit_assignments aa
                    JOIN users au ON au.id = aa.auditor_id
                    WHERE aa.cycle_id = ?
                    """, cycleId
            );
            cycle.put("auditors", auditors);
        }

        return cycles;
    }

    public Map<String, Object> getCycleById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM audit_cycles WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new NotFoundException("Audit cycle not found");
        }
        Map<String, Object> cycle = new LinkedHashMap<>(rows.get(0));

        String scopeLocation = (String) cycle.get("scope_location");
        Long scopeDeptId = cycle.get("scope_department_id") != null ? ((Number) cycle.get("scope_department_id")).longValue() : null;

        StringBuilder sql = new StringBuilder(
                """
                SELECT a.id, a.asset_tag, a.name, a.status, a.location,
                       ar.result, ar.notes, ar.audited_at, au.name AS audited_by_name, au.id AS audited_by_id
                FROM assets a
                LEFT JOIN audit_records ar ON ar.asset_id = a.id AND ar.cycle_id = ?
                LEFT JOIN users au ON au.id = ar.audited_by
                WHERE 1=1
                """
        );
        List<Object> params = new ArrayList<>();
        params.add(id);

        if (scopeLocation != null && !scopeLocation.isBlank()) {
            sql.append(" AND a.location ILIKE ?");
            params.add("%" + scopeLocation.trim() + "%");
        }
        if (scopeDeptId != null) {
            sql.append("""
                 AND EXISTS (
                   SELECT 1 FROM allocations al
                   JOIN users u ON u.id = al.employee_id
                   WHERE al.asset_id = a.id AND al.status = 'active'
                   AND (al.department_id = ? OR u.department_id = ?)
                )
                """);
            params.add(scopeDeptId);
            params.add(scopeDeptId);
        }

        sql.append(" ORDER BY a.asset_tag");
        List<Map<String, Object>> assets = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<Long> auditorIds = jdbcTemplate.query(
                "SELECT auditor_id FROM audit_assignments WHERE cycle_id = ?",
                (rs, rowNum) -> rs.getLong("auditor_id"),
                id
        );

        cycle.put("auditor_ids", auditorIds);
        cycle.put("assets", assets);
        return cycle;
    }

    public Map<String, Object> getCycleSummary(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM audit_cycles WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new NotFoundException("Audit cycle not found");
        }
        Map<String, Object> cycle = rows.get(0);

        String scopeLocation = (String) cycle.get("scope_location");
        Long scopeDeptId = cycle.get("scope_department_id") != null ? ((Number) cycle.get("scope_department_id")).longValue() : null;

        StringBuilder sql = new StringBuilder("SELECT a.id FROM assets a WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (scopeLocation != null && !scopeLocation.isBlank()) {
            sql.append(" AND a.location ILIKE ?");
            params.add("%" + scopeLocation.trim() + "%");
        }
        if (scopeDeptId != null) {
            sql.append("""
                 AND EXISTS (
                   SELECT 1 FROM allocations al
                   JOIN users u ON u.id = al.employee_id
                   WHERE al.asset_id = a.id AND al.status = 'active'
                   AND (al.department_id = ? OR u.department_id = ?)
                )
                """);
            params.add(scopeDeptId);
            params.add(scopeDeptId);
        }

        List<Long> allAssetIds = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getLong("id"), params.toArray());
        int total = allAssetIds.size();

        List<Map<String, Object>> resultRows = jdbcTemplate.queryForList(
                "SELECT result, COUNT(*)::int AS count FROM audit_records WHERE cycle_id = ? GROUP BY result", id
        );
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("verified", 0);
        counts.put("missing", 0);
        counts.put("damaged", 0);

        for (Map<String, Object> r : resultRows) {
            String res = (String) r.get("result");
            int cnt = ((Number) r.get("count")).intValue();
            counts.put(res, cnt);
        }
        int accounted = counts.get("verified") + counts.get("missing") + counts.get("damaged");
        counts.put("unchecked", Math.max(0, total - accounted));

        List<Map<String, Object>> auditorStats = jdbcTemplate.queryForList(
                """
                SELECT u.id, u.name, COUNT(ar.id)::int AS checked
                FROM audit_assignments aa
                JOIN users u ON u.id = aa.auditor_id
                LEFT JOIN audit_records ar ON ar.cycle_id = aa.cycle_id AND ar.audited_by = u.id
                WHERE aa.cycle_id = ?
                GROUP BY u.id, u.name
                """, id
        );

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("counts", counts);
        summary.put("auditor_stats", auditorStats);
        return summary;
    }

    @Transactional
    public Map<String, Object> createCycle(Map<String, Object> req, Long userId, String userRole) {
        checkManager(userRole);

        String name = (String) req.get("name");
        String startDateStr = (String) req.get("start_date");
        String endDateStr = (String) req.get("end_date");

        if (name == null || name.isBlank() || startDateStr == null || endDateStr == null) {
            throw new BadRequestException("name, start_date, end_date required");
        }

        Date startDate = Date.valueOf(startDateStr);
        Date endDate = Date.valueOf(endDateStr);
        Long scopeDeptId = req.get("scope_department_id") != null ? ((Number) req.get("scope_department_id")).longValue() : null;
        String scopeLocation = (String) req.get("scope_location");

        jdbcTemplate.update(
                """
                INSERT INTO audit_cycles (name, scope_department_id, scope_location, start_date, end_date, created_by, status)
                VALUES (?, ?, ?, ?, ?, ?, 'open')
                """,
                name.trim(), scopeDeptId, scopeLocation, startDate, endDate, userId
        );

        Map<String, Object> cycle = jdbcTemplate.queryForMap(
                "SELECT * FROM audit_cycles WHERE name = ? ORDER BY id DESC LIMIT 1", name.trim()
        );
        Long cycleId = ((Number) cycle.get("id")).longValue();

        Object auditorIdsObj = req.get("auditor_ids");
        if (auditorIdsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number num) {
                    Long auditorId = num.longValue();
                    jdbcTemplate.update("INSERT INTO audit_assignments (cycle_id, auditor_id) VALUES (?, ?)", cycleId, auditorId);
                    helperService.notify(auditorId, "audit_assigned", "You've been assigned as auditor on: " + name.trim());
                }
            }
        }

        helperService.logActivity(userId, "audit.created", "audit_cycle", cycleId, name.trim());
        return cycle;
    }

    @Transactional
    public Map<String, Object> updateCycle(Long id, Map<String, Object> req, Long userId, String userRole) {
        checkManager(userRole);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_cycles WHERE id = ? AND status = 'open'", id
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Open audit cycle not found");
        }
        Map<String, Object> current = rows.get(0);

        String name = req.containsKey("name") && req.get("name") != null ? (String) req.get("name") : (String) current.get("name");
        Date startDate = req.containsKey("start_date") && req.get("start_date") != null ? Date.valueOf((String) req.get("start_date")) : (Date) current.get("start_date");
        Date endDate = req.containsKey("end_date") && req.get("end_date") != null ? Date.valueOf((String) req.get("end_date")) : (Date) current.get("end_date");

        jdbcTemplate.update(
                "UPDATE audit_cycles SET name = ?, start_date = ?, end_date = ? WHERE id = ?",
                name, startDate, endDate, id
        );

        if (req.containsKey("auditor_ids") && req.get("auditor_ids") instanceof List<?> list) {
            jdbcTemplate.update("DELETE FROM audit_assignments WHERE cycle_id = ?", id);
            for (Object item : list) {
                if (item instanceof Number num) {
                    jdbcTemplate.update("INSERT INTO audit_assignments (cycle_id, auditor_id) VALUES (?, ?)", id, num.longValue());
                }
            }
        }

        helperService.logActivity(userId, "audit.updated", "audit_cycle", id, name);
        return jdbcTemplate.queryForMap("SELECT * FROM audit_cycles WHERE id = ?", id);
    }

    @Transactional
    public Map<String, Object> recordAudit(Long cycleId, Map<String, Object> req, Long userId, String userRole) {
        Object assetIdObj = req.get("asset_id");
        String result = (String) req.get("result");
        String notes = (String) req.get("notes");

        if (assetIdObj == null || result == null || !VALID_RESULTS.contains(result.toLowerCase())) {
            throw new BadRequestException("result must be verified|missing|damaged");
        }
        Long assetId = ((Number) assetIdObj).longValue();
        String targetResult = result.toLowerCase();

        List<Map<String, Object>> cycles = jdbcTemplate.queryForList(
                "SELECT * FROM audit_cycles WHERE id = ? AND status = 'open'", cycleId
        );
        if (cycles.isEmpty()) {
            throw new NotFoundException("Open audit cycle not found (closed cycles are locked)");
        }
        Map<String, Object> cycle = cycles.get(0);

        // Permission check: admin, asset_manager, or assigned auditor
        if (!MANAGER_ROLES.contains(userRole)) {
            List<Long> assignments = jdbcTemplate.query(
                    "SELECT 1 FROM audit_assignments WHERE cycle_id = ? AND auditor_id = ?",
                    (rs, rowNum) -> 1L,
                    cycleId, userId
            );
            if (assignments.isEmpty()) {
                throw new ForbiddenException("You are not assigned as an auditor for this audit cycle.");
            }
        }

        // Upsert record
        List<Long> existingRecords = jdbcTemplate.query(
                "SELECT id FROM audit_records WHERE cycle_id = ? AND asset_id = ?",
                (rs, rowNum) -> rs.getLong("id"),
                cycleId, assetId
        );

        if (existingRecords.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO audit_records (cycle_id, asset_id, result, notes, audited_by) VALUES (?, ?, ?, ?, ?)",
                    cycleId, assetId, targetResult, notes, userId
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE audit_records SET result = ?, notes = ?, audited_by = ?, audited_at = now() WHERE cycle_id = ? AND asset_id = ?",
                    targetResult, notes, userId, cycleId, assetId
            );
        }

        if (!"verified".equals(targetResult)) {
            helperService.logActivity(userId, "audit.discrepancy", "asset", assetId, targetResult + ": " + (notes != null ? notes : ""));
            List<Map<String, Object>> assets = jdbcTemplate.queryForList("SELECT asset_tag, name FROM assets WHERE id = ?", assetId);
            String tag = !assets.isEmpty() ? (String) assets.get(0).get("asset_tag") : "";
            String assetName = !assets.isEmpty() ? (String) assets.get(0).get("name") : "";
            String cycleName = (String) cycle.get("name");

            List<Long> managers = jdbcTemplate.query(
                    "SELECT id FROM users WHERE role IN ('admin', 'asset_manager') AND status = 'active'",
                    (rs, rowNum) -> rs.getLong("id")
            );
            for (Long mgrId : managers) {
                helperService.notify(
                        mgrId,
                        "audit_discrepancy",
                        "Audit discrepancy: " + tag + " (" + assetName + ") flagged as " + targetResult + " in \"" + cycleName + "\""
                );
            }
        }

        return jdbcTemplate.queryForMap(
                "SELECT * FROM audit_records WHERE cycle_id = ? AND asset_id = ?",
                cycleId, assetId
        );
    }

    public List<Map<String, Object>> getDiscrepancies(Long cycleId) {
        return jdbcTemplate.queryForList(
                """
                SELECT ar.*, a.asset_tag, a.name AS asset_name, a.location, u.name AS audited_by_name
                FROM audit_records ar
                JOIN assets a ON a.id = ar.asset_id
                JOIN users u ON u.id = ar.audited_by
                WHERE ar.cycle_id = ? AND ar.result != 'verified'
                ORDER BY ar.result, a.asset_tag
                """, cycleId
        );
    }

    @Transactional
    public Map<String, Object> closeCycle(Long id, Long userId, String userRole) {
        checkManager(userRole);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_cycles WHERE id = ? AND status = 'open'", id
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Open audit cycle not found");
        }
        Map<String, Object> cycle = rows.get(0);

        jdbcTemplate.update("UPDATE audit_cycles SET status = 'closed', closed_at = now() WHERE id = ?", id);

        jdbcTemplate.update(
                """
                UPDATE assets SET status = 'lost'
                WHERE id IN (SELECT asset_id FROM audit_records WHERE cycle_id = ? AND result = 'missing')
                """, id
        );

        String cycleName = (String) cycle.get("name");
        helperService.logActivity(userId, "audit.closed", "audit_cycle", id, cycleName);

        return jdbcTemplate.queryForMap("SELECT * FROM audit_cycles WHERE id = ?", id);
    }
}
