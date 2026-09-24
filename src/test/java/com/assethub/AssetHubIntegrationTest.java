package com.assethub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AssetHubIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String adminToken;
    private static String employeeToken;

    @Test
    @Order(1)
    public void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    @Order(2)
    public void testAdminLogin() throws Exception {
        Map<String, String> loginReq = Map.of(
                "email", "admin@assetflow.com",
                "password", "admin123"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("admin@assetflow.com"))
                .andExpect(jsonPath("$.user.role").value("admin"))
                .andReturn();

        Map<?, ?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        adminToken = (String) resp.get("token");
    }

    @Test
    @Order(3)
    public void testEmployeeSignupAndMe() throws Exception {
        String testEmail = "testuser" + System.currentTimeMillis() + "@assetflow.com";
        Map<String, String> signupReq = Map.of(
                "name", "Test User",
                "email", testEmail,
                "password", "password123"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("employee"))
                .andReturn();

        Map<?, ?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        employeeToken = (String) resp.get("token");

        // Verify /api/auth/me
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(testEmail))
                .andExpect(jsonPath("$.user.role").value("employee"));
    }

    @Test
    @Order(4)
    public void testOrgEndpoints() throws Exception {
        // GET /api/org/departments
        mockMvc.perform(get("/api/org/departments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        // POST /api/org/departments (Admin only)
        Map<String, Object> deptReq = Map.of("name", "Research & Dev");
        mockMvc.perform(post("/api/org/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Research & Dev"));

        // GET /api/org/categories
        mockMvc.perform(get("/api/org/categories")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));

        // GET /api/org/employees
        mockMvc.perform(get("/api/org/employees")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));
    }

    @Test
    @Order(5)
    public void testAssetScanAndRegistry() throws Exception {
        // Public scan endpoint (NO auth header required)
        mockMvc.perform(get("/api/assets/scan/AF-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asset.asset_tag").value("AF-0001"))
                .andExpect(jsonPath("$.asset.category_name").value("Electronics"))
                .andExpect(jsonPath("$.allocation").isNotEmpty());

        // GET /api/assets
        mockMvc.perform(get("/api/assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));

        // Create new asset (Admin/Manager)
        Map<String, Object> assetReq = Map.of(
                "name", "ThinkPad T14",
                "category_id", 1,
                "serial_number", "SN-TP-9921",
                "condition", "new",
                "location", "HQ Floor 3",
                "is_bookable", true
        );

        MvcResult result = mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assetReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.asset_tag").isNotEmpty())
                .andExpect(jsonPath("$.name").value("ThinkPad T14"))
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Number assetId = (Number) created.get("id");

        // GET /api/assets/:id
        mockMvc.perform(get("/api/assets/" + assetId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ThinkPad T14"));
    }

    @Test
    @Order(6)
    public void testAllocationConflictRuleAndLifecycle() throws Exception {
        // Asset 1 is already allocated to Priya in seed.sql
        // Attempting to allocate it again should trigger 409 Conflict with held_by & hint
        Map<String, Object> allocReq = Map.of(
                "asset_id", 1,
                "employee_id", 5
        );

        mockMvc.perform(post("/api/allocations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allocReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.held_by").value("Priya"))
                .andExpect(jsonPath("$.hint").value("Raise a transfer request instead"));

        // Allocate Asset 2 (which is available) to employee 5
        Map<String, Object> validAllocReq = Map.of(
                "asset_id", 2,
                "employee_id", 5
        );

        MvcResult allocResult = mockMvc.perform(post("/api/allocations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAllocReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.asset_id").value(2))
                .andReturn();

        Map<?, ?> alloc = objectMapper.readValue(allocResult.getResponse().getContentAsString(), Map.class);
        Number allocId = (Number) alloc.get("id");

        // Return flow
        Map<String, Object> returnReq = Map.of(
                "return_notes", "Returned in excellent condition",
                "condition", "good"
        );

        mockMvc.perform(post("/api/allocations/" + allocId + "/return")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("returned"));
    }

    @Test
    @Order(7)
    public void testBookingWorkflowAndOverlap() throws Exception {
        // Room B2 (Asset 3) is bookable
        Map<String, Object> bookReq1 = Map.of(
                "asset_id", 3,
                "start_time", "2026-10-10T10:00:00Z",
                "end_time", "2026-10-10T12:00:00Z",
                "purpose", "Sprint Planning"
        );

        MvcResult result1 = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn();

        Map<?, ?> booking1 = objectMapper.readValue(result1.getResponse().getContentAsString(), Map.class);
        Number bookingId1 = (Number) booking1.get("id");

        // Approve booking 1
        mockMvc.perform(post("/api/bookings/" + bookingId1 + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));

        // Attempting overlapping booking should fail with 409
        Map<String, Object> bookReq2 = Map.of(
                "asset_id", 3,
                "start_time", "2026-10-10T11:00:00Z",
                "end_time", "2026-10-10T13:00:00Z",
                "purpose", "Client Demo"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("Overlaps an existing booking")));
    }

    @Test
    @Order(8)
    public void testMaintenanceStateTransitions() throws Exception {
        Map<String, Object> req = Map.of(
                "asset_id", 1,
                "issue", "Screen flicker issue",
                "priority", "high"
        );

        MvcResult result = mockMvc.perform(post("/api/maintenance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn();

        Map<?, ?> m = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Number mId = (Number) m.get("id");

        // Transition: approve
        mockMvc.perform(put("/api/maintenance/" + mId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));

        // Transition: assign
        mockMvc.perform(put("/api/maintenance/" + mId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "assign", "technician", "Alex"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("assigned"))
                .andExpect(jsonPath("$.technician").value("Alex"));

        // Transition: start
        mockMvc.perform(put("/api/maintenance/" + mId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "start"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"));

        // Transition: resolve
        mockMvc.perform(put("/api/maintenance/" + mId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "resolve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"));
    }

    @Test
    @Order(9)
    public void testTechniciansAndAudits() throws Exception {
        // Create technician
        Map<String, Object> techReq = Map.of(
                "name", "Dave Support",
                "email", "dave@support.com",
                "phone", "555-0199",
                "specialty", "Hardware repair"
        );
        mockMvc.perform(post("/api/technicians")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(techReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dave Support"));

        // List technicians
        mockMvc.perform(get("/api/technicians")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Create Audit Cycle
        Map<String, Object> auditReq = Map.of(
                "name", "Q4 IT Audit",
                "start_date", "2026-10-01",
                "end_date", "2026-10-31",
                "auditor_ids", List.of(2, 3)
        );
        MvcResult auditResult = mockMvc.perform(post("/api/audits")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auditReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Q4 IT Audit"))
                .andReturn();

        Map<?, ?> cycle = objectMapper.readValue(auditResult.getResponse().getContentAsString(), Map.class);
        Number cycleId = (Number) cycle.get("id");

        // Record verification
        Map<String, Object> recordReq = Map.of(
                "asset_id", 1,
                "result", "verified",
                "notes", "Hardware verified in person"
        );
        mockMvc.perform(post("/api/audits/" + cycleId + "/records")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recordReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("verified"));

        // Audit summary
        mockMvc.perform(get("/api/audits/" + cycleId + "/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.verified").value(1));
    }

    @Test
    @Order(10)
    public void testDashboardNotificationsReports() throws Exception {
        // Dashboard
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis").isMap())
                .andExpect(jsonPath("$.recent_activity").isArray());

        // Notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Activity log
        mockMvc.perform(get("/api/activity")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Reports
        mockMvc.perform(get("/api/reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets_by_status").isArray())
                .andExpect(jsonPath("$.department_allocation").isArray());
    }
}
