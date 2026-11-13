package com.assethub.service;

import com.assethub.exception.BadRequestException;
import com.assethub.exception.ConflictException;
import com.assethub.exception.ForbiddenException;
import com.assethub.exception.NotFoundException;
import com.assethub.exception.UnauthorizedException;
import com.assethub.security.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final HelperService helperService;

    // Reset code cache: email -> ResetCodeEntry
    private final Map<String, ResetCodeEntry> resetCodes = new ConcurrentHashMap<>();

    private record ResetCodeEntry(String code, long expiresAt) {}

    public AuthService(JdbcTemplate jdbcTemplate,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       HelperService helperService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.helperService = helperService;
    }

    public Map<String, Object> signup(Map<String, Object> request) {
        String name = (String) request.get("name");
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        if (name == null || name.isBlank() || email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new BadRequestException("name, email and password are required");
        }
        if (password.length() < 6) {
            throw new BadRequestException("Password must be at least 6 characters");
        }

        String lowerEmail = email.trim().toLowerCase();
        List<Long> existing = jdbcTemplate.query(
                "SELECT id FROM users WHERE LOWER(email) = ?",
                (rs, rowNum) -> rs.getLong("id"),
                lowerEmail
        );
        if (!existing.isEmpty()) {
            throw new ConflictException("An account with this email already exists");
        }

        String hash = passwordEncoder.encode(password);
        jdbcTemplate.update(
                "INSERT INTO users (name, email, password_hash, role, status) VALUES (?, ?, ?, 'employee', 'active')",
                name.trim(), lowerEmail, hash
        );

        Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT id, name, email, role, department_id, status FROM users WHERE LOWER(email) = ?",
                lowerEmail
        );

        Long userId = ((Number) user.get("id")).longValue();
        String role = (String) user.get("role");
        String userName = (String) user.get("name");

        helperService.logActivity(userId, "user.signup", "user", userId, userName + " signed up");
        String token = jwtService.generateToken(userId, role, userName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("user", user);
        return response;
    }

    public Map<String, Object> login(Map<String, Object> request) {
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        String lowerEmail = email != null ? email.trim().toLowerCase() : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM users WHERE LOWER(email) = ?",
                lowerEmail
        );
        if (rows.isEmpty() && lowerEmail.endsWith("@assethub.com")) {
            rows = jdbcTemplate.queryForList(
                    "SELECT * FROM users WHERE LOWER(email) = ?",
                    lowerEmail.replace("@assethub.com", "@assetflow.com")
            );
        } else if (rows.isEmpty() && lowerEmail.endsWith("@assetflow.com")) {
            rows = jdbcTemplate.queryForList(
                    "SELECT * FROM users WHERE LOWER(email) = ?",
                    lowerEmail.replace("@assetflow.com", "@assethub.com")
            );
        }

        if (rows.isEmpty()) {
            throw new UnauthorizedException("Invalid email or password");
        }

        Map<String, Object> user = rows.get(0);
        String hash = (String) user.get("password_hash");
        if (password == null || !passwordEncoder.matches(password, hash)) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String status = (String) user.get("status");
        if (!"active".equalsIgnoreCase(status)) {
            throw new ForbiddenException("Account is deactivated");
        }

        Long userId = ((Number) user.get("id")).longValue();
        String role = (String) user.get("role");
        String userName = (String) user.get("name");

        Map<String, Object> safeUser = new LinkedHashMap<>(user);
        safeUser.remove("password_hash");

        String token = jwtService.generateToken(userId, role, userName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("user", safeUser);
        return response;
    }

    public Map<String, Object> getMe(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, email, role, department_id, status FROM users WHERE id = ?",
                userId
        );
        if (rows.isEmpty()) {
            throw new UnauthorizedException("Session invalid");
        }

        Map<String, Object> user = rows.get(0);
        String status = (String) user.get("status");
        if (!"active".equalsIgnoreCase(status)) {
            throw new UnauthorizedException("Session invalid");
        }

        return Map.of("user", user);
    }

    public Map<String, Object> forgotPassword(Map<String, Object> request) {
        String email = (String) request.get("email");
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }

        String lowerEmail = email.trim().toLowerCase();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, email FROM users WHERE LOWER(email) = ? AND status = 'active'",
                lowerEmail
        );
        if (rows.isEmpty() && lowerEmail.endsWith("@assethub.com")) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, name, email FROM users WHERE LOWER(email) = ? AND status = 'active'",
                    lowerEmail.replace("@assethub.com", "@assetflow.com")
            );
        } else if (rows.isEmpty() && lowerEmail.endsWith("@assetflow.com")) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, name, email FROM users WHERE LOWER(email) = ? AND status = 'active'",
                    lowerEmail.replace("@assetflow.com", "@assethub.com")
            );
        }
        if (rows.isEmpty()) {
            throw new NotFoundException("No active account found with this email");
        }

        Map<String, Object> user = rows.get(0);
        String code = String.valueOf(100000 + new Random().nextInt(900000));
        resetCodes.put(lowerEmail, new ResetCodeEntry(code, System.currentTimeMillis() + 15 * 60 * 1000));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Reset code generated");
        response.put("reset_code", code);
        response.put("hint", "In production this would be emailed. Code expires in 15 minutes.");
        response.put("user_name", user.get("name"));
        return response;
    }

    public Map<String, Object> resetPassword(Map<String, Object> request) {
        String email = (String) request.get("email");
        String code = (String) request.get("code");
        String newPassword = (String) request.get("new_password");

        if (email == null || email.isBlank() || code == null || code.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("email, code, and new_password are required");
        }
        if (newPassword.length() < 6) {
            throw new BadRequestException("Password must be at least 6 characters");
        }

        String lowerEmail = email.trim().toLowerCase();
        ResetCodeEntry entry = resetCodes.get(lowerEmail);
        if (entry == null) {
            throw new BadRequestException("No reset code found for this email. Request a new one.");
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            resetCodes.remove(lowerEmail);
            throw new BadRequestException("Reset code has expired. Please request a new one.");
        }
        if (!entry.code().equals(code.trim())) {
            throw new BadRequestException("Invalid reset code");
        }

        String hash = passwordEncoder.encode(newPassword);
        jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE LOWER(email) = ?", hash, lowerEmail);
        resetCodes.remove(lowerEmail);

        helperService.logActivity(null, "user.password_reset", "user", null, "Password reset for " + lowerEmail);
        return Map.of("message", "Password has been reset successfully. You can now sign in.");
    }
}
