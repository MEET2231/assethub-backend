package com.assethub.controller;

import com.assethub.security.UserPrincipal;
import com.assethub.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAuditCycles() {
        return ResponseEntity.ok(auditService.getAuditCycles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCycleById(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getCycleById(id));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<Map<String, Object>> getCycleSummary(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getCycleSummary(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCycle(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> created = auditService.createCycle(request, principal.getId(), principal.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCycle(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(auditService.updateCycle(id, request, principal.getId(), principal.getRole()));
    }

    @PostMapping("/{id}/records")
    public ResponseEntity<Map<String, Object>> recordAudit(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> recorded = auditService.recordAudit(id, request, principal.getId(), principal.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(recorded);
    }

    @GetMapping("/{id}/discrepancies")
    public ResponseEntity<List<Map<String, Object>>> getDiscrepancies(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getDiscrepancies(id));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> closeCycle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(auditService.closeCycle(id, principal.getId(), principal.getRole()));
    }
}
