package com.assethub.controller;

import com.assethub.security.UserPrincipal;
import com.assethub.service.MiscService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MiscController {

    private final MiscService miscService;

    public MiscController(MiscService miscService) {
        this.miscService = miscService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(miscService.getDashboard(principal.getId(), principal.getRole()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Map<String, Object>>> getNotifications(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(miscService.getNotifications(principal.getId()));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Object>> markAllNotificationsRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(miscService.markAllNotificationsRead(principal.getId()));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Object>> markNotificationRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(miscService.markNotificationRead(id, principal.getId()));
    }

    @PostMapping("/notifications/check-overdue")
    public ResponseEntity<Map<String, Object>> checkOverdue() {
        return ResponseEntity.ok(miscService.checkOverdueNotifications());
    }

    @PostMapping("/notifications/booking-reminders")
    public ResponseEntity<Map<String, Object>> checkBookingReminders() {
        return ResponseEntity.ok(miscService.checkBookingReminders());
    }

    @GetMapping("/activity")
    public ResponseEntity<List<Map<String, Object>>> getActivity() {
        return ResponseEntity.ok(miscService.getActivity());
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getReports() {
        return ResponseEntity.ok(miscService.getReports());
    }
}
