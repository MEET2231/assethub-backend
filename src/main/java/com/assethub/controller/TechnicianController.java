package com.assethub.controller;

import com.assethub.security.UserPrincipal;
import com.assethub.service.TechnicianService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {

    private final TechnicianService technicianService;

    public TechnicianController(TechnicianService technicianService) {
        this.technicianService = technicianService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getTechnicians() {
        return ResponseEntity.ok(technicianService.getTechnicians());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTechnician(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> created = technicianService.createTechnician(request, principal.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTechnician(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(technicianService.updateTechnician(id, request, principal.getRole()));
    }
}
