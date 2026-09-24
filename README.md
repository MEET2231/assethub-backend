# AssetHub - Java Spring Boot Backend

AssetHub Backend is a complete, exhaustive, and consistent Java (Spring Boot 3 + Java 17) copy of the AssetFlow backend. It preserves full backward compatibility with the frontend and PostgreSQL database (including Supabase), while offering type safety, high concurrency, and automated testing.

## Tech Stack

- **Java**: 17 (OpenJDK / Temurin)
- **Framework**: Spring Boot 3.3.4 (Spring Web, Spring Security, Spring JDBC)
- **Database**: PostgreSQL (production & Supabase) with zero-config H2 in-memory fallback for local testing
- **Authentication**: JWT (HMAC-SHA256) via JJWT, BCrypt password hashing
- **Build Tool**: Maven (with included `mvnw.cmd` / `mvnw` wrapper)

---

## Features Implemented Exhaustively

1. **Authentication & Session (`/api/auth`)**:
   - `POST /api/auth/signup`: Registers employee with BCrypt password hashing & activity logging.
   - `POST /api/auth/login`: Validates credentials and active account status, issues 12h JWT token.
   - `GET /api/auth/me`: Verifies active session token.
   - `POST /api/auth/forgot-password`: Generates 6-digit code with 15-minute expiry.
   - `POST /api/auth/reset-password`: Validates reset code and updates password.

2. **Organization Management (`/api/org`)**:
   - **Departments**: `GET`, `POST` (admin), `PUT /:id` (admin) with hierarchy (parent_id) and head assignment.
   - **Asset Categories**: `GET`, `POST` (admin), `PUT /:id` (admin) with dynamic JSON `custom_fields`.
   - **Employee Directory**: `GET`, `PUT /:id` (admin) for role promotion/demotion and department assignment with notifications.

3. **Asset Registry (`/api/assets`)**:
   - `GET /api/assets/scan/:tag`: **Public scan endpoint** returning asset details, active holder, last 10 maintenance items, allocation history, transfer requests, and audit results.
   - `GET /api/assets`: Search (`q`) across tag, serial, and name, with filters for category, status, location, bookable flag, and department.
   - `GET /api/assets/:id`: Asset detail with complete history of allocations and maintenance requests.
   - `POST /api/assets`: Creates asset with auto-incremented tag (`AF-0001`, `AF-0002`...).
   - `PUT /api/assets/:id`: Updates specifications and lifecycle status (`available`, `allocated`, `under_maintenance`, `lost`, `retired`, `disposed`).
   - `DELETE /api/assets/:id`: Enforces safety rule preventing deletion of currently allocated assets.

4. **Allocations & Transfers (`/api/allocations`)**:
   - `GET /api/allocations`: Filter by `mine`, `active`, and `overdue` (computes `is_overdue`).
   - `POST /api/allocations`: **Hard Conflict Rule** — blocks allocation if asset is already held, returning holder info and hint to request transfer. Updates status to `allocated`.
   - `POST /api/allocations/:id/return`: Closes allocation with condition notes, reverting asset status to `available`.
   - `GET /api/allocations/transfers`: Full list of transfer requests.
   - `POST /api/allocations/transfers`: Requests transfer for actively allocated asset.
   - `PUT /api/allocations/transfers/:id`: Approves or rejects transfer. Approval automatically closes existing allocation, creates new allocation, and updates asset status.

5. **Resource Bookings (`/api/bookings`)**:
   - `GET /api/bookings`: Calendar view by `asset_id`, user view by `mine`, manager view for all pending/active bookings. Computes `live_status` (`upcoming`, `ongoing`, `completed`, `pending`, `rejected`, `cancelled`).
   - `POST /api/bookings`: **Overlap Validation** — prevents double-booking against existing approved bookings.
   - `POST /api/bookings/:id/cancel`: User cancels pending/approved booking.
   - `POST /api/bookings/:id/approve`: Admin/Manager re-validates overlaps and approves.
   - `POST /api/bookings/:id/reject`: Rejects booking with notification.

6. **Maintenance Workflows (`/api/maintenance`)**:
   - `GET /api/maintenance`: Filter by status (`pending`, `approved`, `assigned`, `in_progress`, `resolved`).
   - `POST /api/maintenance`: Submits maintenance request with priority.
   - `PUT /api/maintenance/:id`: State machine transitions:
     - `approve`: Moves to `approved`, sets asset to `under_maintenance`.
     - `reject`: Rejects request.
     - `assign`: Assigns technician.
     - `start`: Moves to `in_progress`.
     - `resolve`: Moves to `resolved`, automatically restores asset status to `allocated` (if holder exists) or `available`.

7. **Technicians Management (`/api/technicians`)**:
   - `GET /api/technicians`: Lists technicians with active requests count aggregation.
   - `POST /api/technicians`: Registers technician with email uniqueness check.
   - `PUT /api/technicians/:id`: Updates contact info and active/inactive status.

8. **Asset Audits (`/api/audits`)**:
   - `GET /api/audits`: Audit cycles with aggregated records and discrepancy counts.
   - `GET /api/audits/:id`: Scoped assets checklist with audit records.
   - `GET /api/audits/:id/summary`: Breakdown of `verified`, `missing`, `damaged`, and `unchecked` counts, plus auditor contributions.
   - `POST /api/audits`: Creates cycle and notifies assigned auditors.
   - `PUT /api/audits/:id`: Updates open cycle details and assignments.
   - `POST /api/audits/:id/records`: Auditor marks item; automatically alerts all managers on missing/damaged discrepancies.
   - `GET /api/audits/:id/discrepancies`: Discrepancy report.
   - `POST /api/audits/:id/close`: Locks cycle and automatically marks missing assets as `lost`.

9. **Dashboard, Reports & Alerts (`/api/...`)**:
   - `GET /api/dashboard`: Live KPIs, overdue returns, upcoming returns, pending bookings, user assets, recent activity, and booking timeline.
   - `GET /api/notifications`: User notifications with unread counts.
   - `POST /api/notifications/read-all`: Marks notifications read.
   - `PATCH /api/notifications/:id/read`: Marks individual notification read.
   - `POST /api/notifications/check-overdue`: Idempotent overdue return alerts.
   - `POST /api/notifications/booking-reminders`: Upcoming booking reminder alerts within 1 hour.
   - `GET /api/activity`: Full audit trail of system events.
   - `GET /api/reports`: Asset status breakdown, department allocation, maintenance frequency, most used assets, booking heatmap, idle assets, and warranty expirations.

---

## Quick Start (Running Locally)

### 1. Build & Run with Maven Wrapper
From the `assethub-backend` directory:

```bash
# Windows
mvnw.cmd spring-boot:run

# Or run the packaged jar
java -jar target/assethub-backend-1.0.0.jar
```

The API starts on port `5000` (default) matching the frontend dev proxy:
`http://localhost:5000`

### 2. Database Connection Options

1. **Zero-Setup (Default)**:
   If no `DATABASE_URL` is set, an in-memory database with PostgreSQL compatibility starts automatically and loads `schema.sql` and `seed.sql`.

2. **PostgreSQL / Supabase**:
   Set `DATABASE_URL` environment variable or in `application.properties`:
   ```bash
   DATABASE_URL=postgres://postgres:password@localhost:5432/assetflow
   ```

### 3. Demo Credentials

| Role | Email | Password |
|---|---|---|
| Admin | `admin@assetflow.com` | `admin123` |
| Asset Manager | `manager@assetflow.com` | `password123` |
| Department Head | `head@assetflow.com` | `password123` |
| Employee | `priya@assetflow.com` | `password123` |
| Employee | `raj@assetflow.com` | `password123` |

---

## Running the Automated Test Suite

```bash
mvnw.cmd test
```

All 10 integration test classes run automatically, testing authentication, security roles, conflict detection, bookings overlap, state machines, and reporting endpoints.
