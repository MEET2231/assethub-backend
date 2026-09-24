package com.assethub.controller;

import com.assethub.security.UserPrincipal;
import com.assethub.service.OrgService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/org")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    // Departments
    @GetMapping("/departments")
    public ResponseEntity<List<Map<String, Object>>> getDepartments() {
        return ResponseEntity.ok(orgService.getDepartments());
    }

    @PostMapping("/departments")
    public ResponseEntity<Map<String, Object>> createDepartment(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> dept = orgService.createDepartment(request, principal.getId(), principal.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(dept);
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<Map<String, Object>> updateDepartment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orgService.updateDepartment(id, request, principal.getId(), principal.getRole()));
    }

    // Categories
    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> getCategories() {
        return ResponseEntity.ok(orgService.getCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<Map<String, Object>> createCategory(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> cat = orgService.createCategory(request, principal.getId(), principal.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(cat);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orgService.updateCategory(id, request, principal.getId(), principal.getRole()));
    }

    // Employees Directory
    @GetMapping("/employees")
    public ResponseEntity<List<Map<String, Object>>> getEmployees() {
        return ResponseEntity.ok(orgService.getEmployees());
    }

    @PutMapping("/employees/{id}")
    public ResponseEntity<Map<String, Object>> updateEmployee(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orgService.updateEmployee(id, request, principal.getId(), principal.getRole()));
    }
}
