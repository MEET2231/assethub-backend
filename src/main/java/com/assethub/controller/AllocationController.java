package com.assethub.controller;

import com.assethub.security.UserPrincipal;
import com.assethub.service.AllocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllocations(
            @RequestParam(required = false) String mine,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) String overdue,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(allocationService.getAllocations(principal.getId(), mine, active, overdue));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAllocation(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> created = allocationService.createAllocation(request, principal.getId(), principal.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<Map<String, Object>> returnAllocation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(allocationService.returnAllocation(id, request != null ? request : Map.of(), principal.getId()));
    }

    // Transfers
    @GetMapping("/transfers")
    public ResponseEntity<List<Map<String, Object>>> getTransfers() {
        return ResponseEntity.ok(allocationService.getTransfers());
    }

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> createTransfer(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> created = allocationService.createTransfer(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/transfers/{id}")
    public ResponseEntity<Map<String, Object>> decideTransfer(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(allocationService.decideTransfer(id, request, principal.getId(), principal.getRole()));
    }
}
