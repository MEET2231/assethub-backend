package com.assethub.service;

import com.assethub.exception.BadRequestException;
import com.assethub.exception.ConflictException;
import com.assethub.exception.ForbiddenException;
import com.assethub.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.*;

@Service
public class AllocationService {

    private final JdbcTemplate jdbcTemplate;
    private final HelperService helperService;

    private static final Set<String> ALLOCATOR_ROLES = Set.of("admin", "asset_manager", "dept_head");

    public AllocationService(JdbcTemplate jdbcTemplate, HelperService helperService) {
        this.jdbcTemplate = jdbcTemplate;
        this.helperService = helperService;
    }

    private void checkAllocatorRole(String role) {
        if (!ALLOCATOR_ROLES.contains(role)) {
            throw new ForbiddenException("Requires role: admin, asset_manager, or dept_head");
        }
    }

    public List<Map<String, Object>> getAllocations(Long currentUserId, String mine, String active, String overdue) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT al.*, a.asset_tag, a.name AS asset_name, u.name AS employee_name, d.name AS department_name,
                       (al.status = 'active' AND al.expected_return_date < CURRENT_DATE) AS is_overdue
                FROM allocations al
                JOIN assets a ON a.id = al.asset_id
                LEFT JOIN users u ON u.id = al.employee_id
                LEFT JOIN departments d ON d.id = al.department_id
                WHERE 1=1
                """
        );
        List<Object> params = new ArrayList<>();

        if ("true".equalsIgnoreCase(mine)) {
            sql.append(" AND al.employee_id = ?");
            params.add(currentUserId);
        }
        if ("true".equalsIgnoreCase(active)) {
            sql.append(" AND al.status = 'active'");
        }
        if ("true".equalsIgnoreCase(overdue)) {
            sql.append(" AND al.status = 'active' AND al.expected_return_date < CURRENT_DATE");
        }

        sql.append(" ORDER BY al.allocated_at DESC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @Transactional
    public Map<String, Object> createAllocation(Map<String, Object> req, Long userId, String userRole) {
        checkAllocatorRole(userRole);

        Object assetIdObj = req.get("asset_id");
        Object employeeIdObj = req.get("employee_id");
        Object departmentIdObj = req.get("department_id");

        if (assetIdObj == null || (employeeIdObj == null && departmentIdObj == null)) {
            throw new BadRequestException("asset_id and an employee_id or department_id are required");
        }

        Long assetId = ((Number) assetIdObj).longValue();
        Long employeeId = employeeIdObj != null ? ((Number) employeeIdObj).longValue() : null;
        Long departmentId = departmentIdObj != null ? ((Number) departmentIdObj).longValue() : null;

        // Hard conflict check: is asset currently held?
        List<Map<String, Object>> existingAllocs = jdbcTemplate.queryForList(
                """
                SELECT al.id, u.name AS holder_name, d.name AS holder_department
                FROM allocations al
                LEFT JOIN users u ON u.id = al.employee_id
                LEFT JOIN departments d ON d.id = al.department_id
                WHERE al.asset_id = ? AND al.status = 'active'
                """, assetId
        );

        if (!existingAllocs.isEmpty()) {
            Map<String, Object> existing = existingAllocs.get(0);
            String holder = (String) existing.get("holder_name");
            if (holder == null || holder.isBlank()) {
                holder = (String) existing.get("holder_department");
            }
            Long existingAllocId = ((Number) existing.get("id")).longValue();

            throw new ConflictException(
                    "Asset is currently held by " + holder,
                    holder,
                    existingAllocId,
                    "Raise a transfer request instead"
            );
        }

        List<Map<String, Object>> assets = jdbcTemplate.queryForList("SELECT * FROM assets WHERE id = ?", assetId);
        if (assets.isEmpty()) {
            throw new NotFoundException("Asset not found");
        }
        Map<String, Object> asset = assets.get(0);
        String assetStatus = (String) asset.get("status");
        if (!"available".equalsIgnoreCase(assetStatus)) {
            throw new ConflictException("Asset is " + assetStatus + ", not available");
        }

        Object returnDateObj = req.get("expected_return_date");
        Date returnDate = HelperService.parseDate(returnDateObj);

        jdbcTemplate.update(
                """
                INSERT INTO allocations (asset_id, employee_id, department_id, allocated_by, expected_return_date)
                VALUES (?, ?, ?, ?, ?)
                """,
                assetId, employeeId, departmentId, userId, returnDate
        );

        jdbcTemplate.update("UPDATE assets SET status = 'allocated' WHERE id = ?", assetId);

        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT * FROM allocations WHERE asset_id = ? AND status = 'active' ORDER BY id DESC LIMIT 1",
                assetId
        );

        String assetTag = (String) asset.get("asset_tag");
        String assetName = (String) asset.get("name");
        helperService.logActivity(userId, "allocation.created", "asset", assetId, assetTag + " allocated");

        if (employeeId != null) {
            helperService.notify(employeeId, "asset_assigned", assetName + " (" + assetTag + ") has been assigned to you");
        }

        return created;
    }

    @Transactional
    public Map<String, Object> returnAllocation(Long allocationId, Map<String, Object> req, Long userId) {
        String returnNotes = (String) req.get("return_notes");
        String condition = (String) req.get("condition");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM allocations WHERE id = ? AND status = 'active'", allocationId
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Active allocation not found");
        }
        Map<String, Object> alloc = rows.get(0);
        Long assetId = ((Number) alloc.get("asset_id")).longValue();

        jdbcTemplate.update(
                "UPDATE allocations SET status = 'returned', returned_at = now(), return_notes = ? WHERE id = ?",
                returnNotes, allocationId
        );

        if (condition != null && !condition.isBlank()) {
            jdbcTemplate.update("UPDATE assets SET status = 'available', condition = ? WHERE id = ?", condition, assetId);
        } else {
            jdbcTemplate.update("UPDATE assets SET status = 'available' WHERE id = ?", assetId);
        }

        helperService.logActivity(userId, "allocation.returned", "asset", assetId, returnNotes);
        return jdbcTemplate.queryForMap("SELECT * FROM allocations WHERE id = ?", allocationId);
    }

    // Transfers
    public List<Map<String, Object>> getTransfers() {
        return jdbcTemplate.queryForList(
                """
                SELECT t.*, a.asset_tag, a.name AS asset_name, rb.name AS requested_by_name,
                       te.name AS to_employee_name, te.department_id AS to_employee_dept_id,
                       td.name AS to_department_name, db.name AS decided_by_name
                FROM transfer_requests t
                JOIN assets a ON a.id = t.asset_id
                JOIN users rb ON rb.id = t.requested_by
                LEFT JOIN users te ON te.id = t.to_employee_id
                LEFT JOIN departments td ON td.id = t.to_department_id
                LEFT JOIN users db ON db.id = t.decided_by
                ORDER BY t.created_at DESC
                """
        );
    }

    public Map<String, Object> createTransfer(Map<String, Object> req, Long userId) {
        Object assetIdObj = req.get("asset_id");
        if (assetIdObj == null) {
            throw new BadRequestException("asset_id is required");
        }
        Long assetId = ((Number) assetIdObj).longValue();

        List<Long> activeAllocs = jdbcTemplate.query(
                "SELECT id FROM allocations WHERE asset_id = ? AND status = 'active'",
                (rs, rowNum) -> rs.getLong("id"),
                assetId
        );
        if (activeAllocs.isEmpty()) {
            throw new BadRequestException("Asset is not allocated — allocate it directly instead");
        }
        Long fromAllocationId = activeAllocs.get(0);

        Long toEmployeeId = req.get("to_employee_id") != null ? ((Number) req.get("to_employee_id")).longValue() : null;
        Long toDepartmentId = req.get("to_department_id") != null ? ((Number) req.get("to_department_id")).longValue() : null;
        String reason = (String) req.get("reason");

        jdbcTemplate.update(
                """
                INSERT INTO transfer_requests (asset_id, from_allocation_id, requested_by, to_employee_id, to_department_id, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                assetId, fromAllocationId, userId, toEmployeeId, toDepartmentId, reason
        );

        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT * FROM transfer_requests WHERE asset_id = ? ORDER BY id DESC LIMIT 1",
                assetId
        );

        helperService.logActivity(userId, "transfer.requested", "asset", assetId, reason);
        return created;
    }

    @Transactional
    public Map<String, Object> decideTransfer(Long transferId, Map<String, Object> req, Long userId, String userRole) {
        checkAllocatorRole(userRole);

        String action = (String) req.get("action");
        if (!"approve".equalsIgnoreCase(action) && !"reject".equalsIgnoreCase(action)) {
            throw new BadRequestException("action must be 'approve' or 'reject'");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM transfer_requests WHERE id = ? AND status = 'pending'",
                transferId
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Pending transfer not found");
        }
        Map<String, Object> transfer = rows.get(0);

        String newStatus = "approve".equalsIgnoreCase(action) ? "approved" : "rejected";
        jdbcTemplate.update(
                "UPDATE transfer_requests SET status = ?, decided_by = ?, decided_at = now() WHERE id = ?",
                newStatus, userId, transferId
        );

        Long assetId = ((Number) transfer.get("asset_id")).longValue();
        Long fromAllocationId = ((Number) transfer.get("from_allocation_id")).longValue();
        Long toEmployeeId = transfer.get("to_employee_id") != null ? ((Number) transfer.get("to_employee_id")).longValue() : null;
        Long toDepartmentId = transfer.get("to_department_id") != null ? ((Number) transfer.get("to_department_id")).longValue() : null;
        Long requestedBy = ((Number) transfer.get("requested_by")).longValue();

        if ("approved".equals(newStatus)) {
            jdbcTemplate.update(
                    "UPDATE allocations SET status = 'returned', returned_at = now(), return_notes = 'Transferred' WHERE id = ?",
                    fromAllocationId
            );
            jdbcTemplate.update(
                    "INSERT INTO allocations (asset_id, employee_id, department_id, allocated_by) VALUES (?, ?, ?, ?)",
                    assetId, toEmployeeId, toDepartmentId, userId
            );
            jdbcTemplate.update("UPDATE assets SET status = 'allocated' WHERE id = ?", assetId);

            if (toEmployeeId != null) {
                helperService.notify(toEmployeeId, "transfer_approved", "A transferred asset has been assigned to you");
            }
        }

        helperService.notify(requestedBy, "transfer_" + newStatus, "Your transfer request was " + newStatus);
        helperService.logActivity(userId, "transfer." + newStatus, "asset", assetId, null);

        return jdbcTemplate.queryForMap("SELECT * FROM transfer_requests WHERE id = ?", transferId);
    }
}
