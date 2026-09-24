package com.assethub.controller;

import com.assethub.security.UserPrincipal;
import com.assethub.service.AssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    // Public scan endpoint
    @GetMapping("/scan/{tag}")
    public ResponseEntity<Map<String, Object>> scanAsset(@PathVariable String tag) {
        return ResponseEntity.ok(assetService.scanAsset(tag));
    }

    // Search and filter
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAssets(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String bookable,
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(assetService.getAssets(q, category, status, location, bookable, department));
    }

    // Asset detail + full history
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAssetById(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.getAssetById(id));
    }

    // Create asset
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAsset(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> created = assetService.createAsset(request, principal.getId(), principal.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Update asset
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAsset(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assetService.updateAsset(id, request, principal.getId(), principal.getRole()));
    }

    // Delete asset
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAsset(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assetService.deleteAsset(id, principal.getId(), principal.getRole()));
    }
}
