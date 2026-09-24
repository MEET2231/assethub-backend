package com.assethub.service;

import com.assethub.exception.BadRequestException;
import com.assethub.exception.ConflictException;
import com.assethub.exception.ForbiddenException;
import com.assethub.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;

@Service
public class AssetService {

    private final JdbcTemplate jdbcTemplate;
    private final HelperService helperService;
    private final ObjectMapper objectMapper;

    private static final Set<String> MANAGER_ROLES = Set.of("admin", "asset_manager");
    private static final Set<String> VALID_STATUSES = Set.of(
            "available", "allocated", "reserved", "under_maintenance", "lost", "retired", "disposed"
    );

    public AssetService(JdbcTemplate jdbcTemplate, HelperService helperService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.helperService = helperService;
        this.objectMapper = objectMapper;
    }

    private void checkManager(String role) {
        if (!MANAGER_ROLES.contains(role)) {
            throw new ForbiddenException("Requires role: admin or asset_manager");
        }
    }

    public Map<String, Object> scanAsset(String tag) {
        List<Map<String, Object>> assets = jdbcTemplate.queryForList(
                """
                SELECT a.*, c.name AS category_name
                FROM assets a
                JOIN categories c ON c.id = a.category_id
                WHERE a.asset_tag = ?
                """, tag
        );
        if (assets.isEmpty()) {
            throw new NotFoundException("Asset not found");
        }
        Map<String, Object> asset = assets.get(0);
        Long assetId = ((Number) asset.get("id")).longValue();

        List<Map<String, Object>> allocs = jdbcTemplate.queryForList(
                """
                SELECT al.*, u.name AS employee_name, u.email AS employee_email,
                       d.name AS department_name, by_u.name AS allocated_by_name
                FROM allocations al
                LEFT JOIN users u ON u.id = al.employee_id
                LEFT JOIN departments d ON d.id = COALESCE(al.department_id, u.department_id)
                LEFT JOIN users by_u ON by_u.id = al.allocated_by
                WHERE al.asset_id = ? AND al.status = 'active'
                """, assetId
        );
        Map<String, Object> allocation = allocs.isEmpty() ? null : allocs.get(0);

        List<Map<String, Object>> maintenance = jdbcTemplate.queryForList(
                """
                SELECT m.status, m.issue, m.priority, m.created_at, m.resolved_at, u.name AS raised_by_name
                FROM maintenance_requests m
                JOIN users u ON u.id = m.raised_by
                WHERE m.asset_id = ?
                ORDER BY m.created_at DESC LIMIT 10
                """, assetId
        );

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                """
                SELECT al.*, u.name AS employee_name, d.name AS department_name, by_u.name AS allocated_by_name
                FROM allocations al
                LEFT JOIN users u ON u.id = al.employee_id
                LEFT JOIN departments d ON d.id = al.department_id
                LEFT JOIN users by_u ON by_u.id = al.allocated_by
                WHERE al.asset_id = ?
                ORDER BY al.allocated_at DESC
                """, assetId
        );

        List<Map<String, Object>> transfers = jdbcTemplate.queryForList(
                """
                SELECT t.*, a.asset_tag, rb.name AS requested_by_name,
                       te.name AS to_employee_name, td.name AS to_department_name
                FROM transfer_requests t
                JOIN assets a ON a.id = t.asset_id
                JOIN users rb ON rb.id = t.requested_by
                LEFT JOIN users te ON te.id = t.to_employee_id
                LEFT JOIN departments td ON td.id = t.to_department_id
                WHERE t.asset_id = ?
                ORDER BY t.created_at DESC
                """, assetId
        );

        List<Map<String, Object>> audits = jdbcTemplate.queryForList(
                """
                SELECT ar.result, ar.notes, ar.audited_at, ac.name AS cycle_name, u.name AS audited_by_name
                FROM audit_records ar
                JOIN audit_cycles ac ON ac.id = ar.cycle_id
                JOIN users u ON u.id = ar.audited_by
                WHERE ar.asset_id = ?
                ORDER BY ar.audited_at DESC
                """, assetId
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("asset", asset);
        response.put("allocation", allocation);
        response.put("maintenance", maintenance);
        response.put("history", history);
        response.put("transfers", transfers);
        response.put("audits", audits);
        return response;
    }

    public List<Map<String, Object>> getAssets(String q, String category, String status, String location, String bookable, String department) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT a.*, c.name AS category_name,
                       holder.name AS holder_name, hd.name AS holder_department
                FROM assets a
                JOIN categories c ON c.id = a.category_id
                LEFT JOIN allocations al ON al.asset_id = a.id AND al.status = 'active'
                LEFT JOIN users holder ON holder.id = al.employee_id
                LEFT JOIN departments hd ON hd.id = al.department_id
                WHERE 1=1
                """
        );
        List<Object> params = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            sql.append(" AND (a.asset_tag ILIKE ? OR a.serial_number ILIKE ? OR a.name ILIKE ?)");
            String pattern = "%" + q.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND a.category_id = ?");
            params.add(Long.parseLong(category.trim()));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            params.add(status.trim());
        }
        if (location != null && !location.isBlank()) {
            sql.append(" AND a.location ILIKE ?");
            params.add("%" + location.trim() + "%");
        }
        if ("true".equalsIgnoreCase(bookable)) {
            sql.append(" AND a.is_bookable = true");
        }
        if (department != null && !department.isBlank()) {
            long deptId = Long.parseLong(department.trim());
            sql.append("""
                 AND EXISTS (
                   SELECT 1 FROM allocations al2
                   JOIN users u2 ON u2.id = al2.employee_id
                   WHERE al2.asset_id = a.id AND al2.status = 'active'
                   AND (al2.department_id = ? OR u2.department_id = ?)
                )
                """);
            params.add(deptId);
            params.add(deptId);
        }

        sql.append(" ORDER BY a.asset_tag");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> getAssetById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT a.*, c.name AS category_name
                FROM assets a
                JOIN categories c ON c.id = a.category_id
                WHERE a.id = ?
                """, id
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Asset not found");
        }
        Map<String, Object> asset = new LinkedHashMap<>(rows.get(0));

        List<Map<String, Object>> allocations = jdbcTemplate.queryForList(
                """
                SELECT al.*, u.name AS employee_name, d.name AS department_name, by_u.name AS allocated_by_name
                FROM allocations al
                LEFT JOIN users u ON u.id = al.employee_id
                LEFT JOIN departments d ON d.id = al.department_id
                LEFT JOIN users by_u ON by_u.id = al.allocated_by
                WHERE al.asset_id = ?
                ORDER BY al.allocated_at DESC
                """, id
        );

        List<Map<String, Object>> maintenance = jdbcTemplate.queryForList(
                """
                SELECT m.*, u.name AS raised_by_name
                FROM maintenance_requests m
                JOIN users u ON u.id = m.raised_by
                WHERE m.asset_id = ?
                ORDER BY m.created_at DESC
                """, id
        );

        asset.put("allocations", allocations);
        asset.put("maintenance", maintenance);
        return asset;
    }

    public Map<String, Object> createAsset(Map<String, Object> req, Long userId, String userRole) {
        checkManager(userRole);

        String name = (String) req.get("name");
        Object categoryIdObj = req.get("category_id");
        if (name == null || name.isBlank() || categoryIdObj == null) {
            throw new BadRequestException("name and category_id are required");
        }
        Long categoryId = ((Number) categoryIdObj).longValue();

        String tag = helperService.nextAssetTag();
        String serialNumber = (String) req.get("serial_number");
        String acqDateStr = (String) req.get("acquisition_date");
        Date acqDate = acqDateStr != null && !acqDateStr.isBlank() ? Date.valueOf(acqDateStr) : null;
        BigDecimal acqCost = req.get("acquisition_cost") != null ? new BigDecimal(String.valueOf(req.get("acquisition_cost"))) : null;
        String condition = req.get("condition") != null ? (String) req.get("condition") : "good";
        String location = (String) req.get("location");
        String imageUrl = (String) req.get("image_url");
        boolean isBookable = Boolean.TRUE.equals(req.get("is_bookable"));
        String brand = (String) req.get("brand");
        String model = (String) req.get("model");
        String vendor = (String) req.get("vendor");
        String warrantyStr = (String) req.get("warranty_expiry");
        Date warrantyExpiry = warrantyStr != null && !warrantyStr.isBlank() ? Date.valueOf(warrantyStr) : null;

        String customValuesJson = "{}";
        if (req.containsKey("custom_values") && req.get("custom_values") != null) {
            try {
                customValuesJson = objectMapper.writeValueAsString(req.get("custom_values"));
            } catch (Exception ignored) {
            }
        }

        jdbcTemplate.update(
                """
                INSERT INTO assets (asset_tag, name, category_id, serial_number, acquisition_date, acquisition_cost,
                                    condition, location, image_url, is_bookable, custom_values, created_by,
                                    brand, model, vendor, warranty_expiry)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tag, name.trim(), categoryId, serialNumber, acqDate, acqCost,
                condition, location, imageUrl, isBookable, customValuesJson, userId,
                brand, model, vendor, warrantyExpiry
        );

        Map<String, Object> created = jdbcTemplate.queryForMap("SELECT * FROM assets WHERE asset_tag = ?", tag);
        Long assetId = ((Number) created.get("id")).longValue();
        helperService.logActivity(userId, "asset.created", "asset", assetId, tag + " " + name.trim());
        return created;
    }

    public Map<String, Object> updateAsset(Long id, Map<String, Object> req, Long userId, String userRole) {
        checkManager(userRole);

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM assets WHERE id = ?", id);
        if (existing.isEmpty()) {
            throw new NotFoundException("Asset not found");
        }
        Map<String, Object> current = existing.get(0);

        String status = (String) req.get("status");
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new BadRequestException("Invalid status");
        }

        String name = req.containsKey("name") && req.get("name") != null ? (String) req.get("name") : (String) current.get("name");
        String condition = req.containsKey("condition") && req.get("condition") != null ? (String) req.get("condition") : (String) current.get("condition");
        String location = req.containsKey("location") ? (String) req.get("location") : (String) current.get("location");
        String targetStatus = status != null ? status : (String) current.get("status");
        Boolean isBookable = req.containsKey("is_bookable") && req.get("is_bookable") != null ? (Boolean) req.get("is_bookable") : (Boolean) current.get("is_bookable");
        String imageUrl = req.containsKey("image_url") ? (String) req.get("image_url") : (String) current.get("image_url");
        String brand = req.containsKey("brand") ? (String) req.get("brand") : (String) current.get("brand");
        String model = req.containsKey("model") ? (String) req.get("model") : (String) current.get("model");
        String vendor = req.containsKey("vendor") ? (String) req.get("vendor") : (String) current.get("vendor");

        Date warrantyExpiry = null;
        if (req.containsKey("warranty_expiry")) {
            String wStr = (String) req.get("warranty_expiry");
            warrantyExpiry = (wStr != null && !wStr.isBlank()) ? Date.valueOf(wStr) : null;
        } else if (current.get("warranty_expiry") != null) {
            warrantyExpiry = (Date) current.get("warranty_expiry");
        }

        jdbcTemplate.update(
                """
                UPDATE assets SET name = ?, condition = ?, location = ?, status = ?,
                                  is_bookable = ?, image_url = ?, brand = ?, model = ?,
                                  vendor = ?, warranty_expiry = ?
                WHERE id = ?
                """,
                name, condition, location, targetStatus, isBookable, imageUrl, brand, model, vendor, warrantyExpiry, id
        );

        Map<String, Object> updated = jdbcTemplate.queryForMap("SELECT * FROM assets WHERE id = ?", id);
        String assetTag = (String) updated.get("asset_tag");
        helperService.logActivity(userId, "asset.updated", "asset", id, assetTag + (status != null ? " → " + status : ""));
        return updated;
    }

    public Map<String, Object> deleteAsset(Long id, Long userId, String userRole) {
        checkManager(userRole);

        List<Long> activeAllocations = jdbcTemplate.query(
                "SELECT id FROM allocations WHERE asset_id = ? AND status = 'active'",
                (rs, rowNum) -> rs.getLong("id"),
                id
        );
        if (!activeAllocations.isEmpty()) {
            throw new ConflictException("Cannot delete an asset that is currently allocated. Return it first.");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM assets WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new NotFoundException("Asset not found");
        }
        Map<String, Object> asset = rows.get(0);
        String tag = (String) asset.get("asset_tag");
        String name = (String) asset.get("name");

        jdbcTemplate.update("DELETE FROM assets WHERE id = ?", id);
        helperService.logActivity(userId, "asset.deleted", "asset", id, tag + " " + name);
        return Map.of("ok", true);
    }
}
