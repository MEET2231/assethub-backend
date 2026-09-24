package com.assethub.controller;

import com.assethub.security.UserPrincipal;
import com.assethub.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getBookings(
            @RequestParam(required = false) String asset_id,
            @RequestParam(required = false) String mine,
            @RequestParam(required = false) String include_cancelled,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(bookingService.getBookings(
                principal.getId(), principal.getRole(), asset_id, mine, include_cancelled
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createBooking(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> created = bookingService.createBooking(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(bookingService.cancelBooking(id, principal.getId()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(bookingService.approveBooking(id, principal.getId(), principal.getRole()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(bookingService.rejectBooking(id, principal.getId(), principal.getRole()));
    }
}
