package com.assethub.controller;

import com.assethub.security.UserPrincipal;
import com.assethub.service.MaintenanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRequests(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(maintenanceService.getRequests(status));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> created = maintenanceService.createRequest(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> transitionRequest(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(maintenanceService.transitionRequest(id, request, principal.getId(), principal.getRole()));
    }
}
