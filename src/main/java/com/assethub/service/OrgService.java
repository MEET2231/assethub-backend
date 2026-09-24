package com.assethub.service;

import com.assethub.exception.BadRequestException;
import com.assethub.exception.ForbiddenException;
import com.assethub.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrgService {

    private final JdbcTemplate jdbcTemplate;
    private final HelperService helperService;
    private final ObjectMapper objectMapper;

    private static final Set<String> VALID_ROLES = Set.of("admin", "asset_manager", "dept_head", "employee");

    public OrgService(JdbcTemplate jdbcTemplate, HelperService helperService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.helperService = helperService;
        this.objectMapper = objectMapper;
    }

    private void checkAdmin(String role) {
        if (!"admin".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Requires role: admin");
        }
    }

    // Departments
    public List<Map<String, Object>> getDepartments() {
        return jdbcTemplate.queryForList(
                """
                SELECT d.*, u.name AS head_name, p.name AS parent_name
                FROM departments d
                LEFT JOIN users u ON u.id = d.head_id
                LEFT JOIN departments p ON p.id = d.parent_id
                ORDER BY d.name
                """
        );
    }

    public Map<String, Object> createDepartment(Map<String, Object> request, Long adminId, String adminRole) {
        checkAdmin(adminRole);
        String name = (String) request.get("name");
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }

        Long headId = request.get("head_id") != null ? ((Number) request.get("head_id")).longValue() : null;
        Long parentId = request.get("parent_id") != null ? ((Number) request.get("parent_id")).longValue() : null;

        jdbcTemplate.update(
                "INSERT INTO departments (name, head_id, parent_id) VALUES (?, ?, ?)",
                name.trim(), headId, parentId
        );

        Map<String, Object> dept = jdbcTemplate.queryForMap(
                "SELECT * FROM departments WHERE name = ?",
                name.trim()
        );

        Long deptId = ((Number) dept.get("id")).longValue();
        helperService.logActivity(adminId, "department.created", "department", deptId, name.trim());
        return dept;
    }

    public Map<String, Object> updateDepartment(Long id, Map<String, Object> request, Long adminId, String adminRole) {
        checkAdmin(adminRole);

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM departments WHERE id = ?", id);
        if (existing.isEmpty()) {
            throw new NotFoundException("Department not found");
        }
        Map<String, Object> current = existing.get(0);

        String name = request.containsKey("name") && request.get("name") != null ? (String) request.get("name") : (String) current.get("name");
        Long headId = request.containsKey("head_id") ? (request.get("head_id") != null ? ((Number) request.get("head_id")).longValue() : null) : (current.get("head_id") != null ? ((Number) current.get("head_id")).longValue() : null);
        Long parentId = request.containsKey("parent_id") ? (request.get("parent_id") != null ? ((Number) request.get("parent_id")).longValue() : null) : (current.get("parent_id") != null ? ((Number) current.get("parent_id")).longValue() : null);
        String status = request.containsKey("status") && request.get("status") != null ? (String) request.get("status") : (String) current.get("status");

        jdbcTemplate.update(
                "UPDATE departments SET name = ?, head_id = ?, parent_id = ?, status = ? WHERE id = ?",
                name, headId, parentId, status, id
        );

        Map<String, Object> updated = jdbcTemplate.queryForMap("SELECT * FROM departments WHERE id = ?", id);
        helperService.logActivity(adminId, "department.updated", "department", id, name);
        return updated;
    }

    // Categories
    public List<Map<String, Object>> getCategories() {
        return jdbcTemplate.queryForList("SELECT * FROM categories ORDER BY name");
    }

    public Map<String, Object> createCategory(Map<String, Object> request, Long adminId, String adminRole) {
        checkAdmin(adminRole);
        String name = (String) request.get("name");
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }

        String description = (String) request.get("description");
        Object customFieldsObj = request.get("custom_fields");
        String customFieldsJson = "[]";
        try {
            if (customFieldsObj != null) {
                customFieldsJson = objectMapper.writeValueAsString(customFieldsObj);
            }
        } catch (Exception ignored) {
        }

        jdbcTemplate.update(
                "INSERT INTO categories (name, description, custom_fields) VALUES (?, ?, ?)",
                name.trim(), description, customFieldsJson
        );

        Map<String, Object> cat = jdbcTemplate.queryForMap("SELECT * FROM categories WHERE name = ?", name.trim());
        Long catId = ((Number) cat.get("id")).longValue();
        helperService.logActivity(adminId, "category.created", "category", catId, name.trim());
        return cat;
    }

    public Map<String, Object> updateCategory(Long id, Map<String, Object> request, Long adminId, String adminRole) {
        checkAdmin(adminRole);

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM categories WHERE id = ?", id);
        if (existing.isEmpty()) {
            throw new NotFoundException("Category not found");
        }
        Map<String, Object> current = existing.get(0);

        String name = request.containsKey("name") && request.get("name") != null ? (String) request.get("name") : (String) current.get("name");
        String description = request.containsKey("description") ? (String) request.get("description") : (String) current.get("description");

        String customFieldsJson = "[]";
        if (request.containsKey("custom_fields")) {
            try {
                customFieldsJson = objectMapper.writeValueAsString(request.get("custom_fields"));
            } catch (Exception ignored) {
            }
            jdbcTemplate.update(
                    "UPDATE categories SET name = ?, description = ?, custom_fields = ? WHERE id = ?",
                    name, description, customFieldsJson, id
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE categories SET name = ?, description = ? WHERE id = ?",
                    name, description, id
            );
        }

        return jdbcTemplate.queryForMap("SELECT * FROM categories WHERE id = ?", id);
    }

    // Employees Directory
    public List<Map<String, Object>> getEmployees() {
        return jdbcTemplate.queryForList(
                """
                SELECT u.id, u.name, u.email, u.role, u.status, u.department_id, d.name AS department_name
                FROM users u
                LEFT JOIN departments d ON d.id = u.department_id
                ORDER BY u.name
                """
        );
    }

    public Map<String, Object> updateEmployee(Long id, Map<String, Object> request, Long adminId, String adminRole) {
        checkAdmin(adminRole);

        String role = (String) request.get("role");
        if (role != null && !VALID_ROLES.contains(role)) {
            throw new BadRequestException("Invalid role");
        }

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT * FROM users WHERE id = ?", id);
        if (existing.isEmpty()) {
            throw new NotFoundException("User not found");
        }
        Map<String, Object> current = existing.get(0);

        String targetRole = role != null ? role : (String) current.get("role");
        Long departmentId = request.containsKey("department_id") ? (request.get("department_id") != null ? ((Number) request.get("department_id")).longValue() : null) : (current.get("department_id") != null ? ((Number) current.get("department_id")).longValue() : null);
        String status = request.containsKey("status") && request.get("status") != null ? (String) request.get("status") : (String) current.get("status");

        jdbcTemplate.update(
                "UPDATE users SET role = ?, department_id = ?, status = ? WHERE id = ?",
                targetRole, departmentId, status, id
        );

        Map<String, Object> updated = jdbcTemplate.queryForMap(
                "SELECT id, name, email, role, department_id, status FROM users WHERE id = ?", id
        );

        String userName = (String) updated.get("name");
        helperService.logActivity(adminId, "user.role_changed", "user", id, userName + " → " + targetRole);
        helperService.notify(id, "role_changed", "Your role is now: " + targetRole.replace('_', ' '));

        return updated;
    }
}
