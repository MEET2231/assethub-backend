-- AssetHub presentation seed data
-- Passwords: admin@assethub.com / admin123 | admin@assetflow.com / admin123 | all others / password123

-- 1. Departments
INSERT INTO departments (name) VALUES 
  ('IT'),
  ('Operations'),
  ('Engineering'),
  ('Product & Design'),
  ('Human Resources');

-- 2. Users (Admin, Managers, Dept Heads, and Employees)
INSERT INTO users (name, email, password_hash, role, department_id) VALUES
  ('Admin',           'admin@assetflow.com',   '$2a$10$wIoFXaCuLL.qV1qRL6VSZONLMjUTa49/mw9CnWqFZ0ykn7NWpt9V2', 'admin',         NULL),
  ('Meera Nair',      'manager@assetflow.com', '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'asset_manager', 1),
  ('Harish Verma',    'head@assetflow.com',    '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'dept_head',     2),
  ('Priya',    'priya@assetflow.com',   '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'employee',      1),
  ('Raj',    'raj@assetflow.com',     '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'employee',      2),
  ('Admin',           'admin@assethub.com',    '$2a$10$wIoFXaCuLL.qV1qRL6VSZONLMjUTa49/mw9CnWqFZ0ykn7NWpt9V2', 'admin',         NULL),
  ('Aarav Patel',     'aarav@assethub.com',    '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'employee',      3),
  ('Sneha Rao',       'sneha@assethub.com',    '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'employee',      4),
  ('Vikram Malhotra', 'vikram@assethub.com',   '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'dept_head',     3),
  ('Kavita Desai',    'kavita@assethub.com',   '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'employee',      5);

-- Set department heads
UPDATE departments SET head_id = 3 WHERE id = 2;
UPDATE departments SET head_id = 9 WHERE id = 3;

-- 3. Technicians
INSERT INTO technicians (name, email, phone, specialty, status) VALUES
  ('Alex Miller',     'alex.m@service.internal',  '+91 98201 11223', 'Hardware & Display Repairs', 'active'),
  ('David Chen',      'david.c@service.internal', '+91 98201 22334', 'Networking & OS Diagnostics', 'active'),
  ('Sara Al-Mansoor', 'sara.a@service.internal',  '+91 98201 33445', 'AV Systems & Facility Gear', 'active');

-- 4. Categories
INSERT INTO categories (name, description, custom_fields) VALUES
  ('Electronics',       'Laptops, workstations, and computing gear', '[{"name":"RAM (GB)","type":"number"},{"name":"OS","type":"text"}]'),
  ('Meeting Rooms',     'Bookable conference rooms and shared spaces', '[{"name":"Capacity","type":"number"}]'),
  ('Vehicles',          'Company fleet, executive cars, and transport', '[{"name":"Fuel Type","type":"text"}]'),
  ('Monitors & Displays','4K/5K displays, ultrawides, and studio monitors', '[{"name":"Screen Size","type":"text"}]'),
  ('Mobile & Tablets',  'Smartphones, iPads, and field testing devices', '[{"name":"Storage","type":"text"}]');

-- 5. Assets (15 rich, diverse items across categories, brands, locations, and conditions)
INSERT INTO assets (asset_tag, name, category_id, serial_number, acquisition_date, acquisition_cost, condition, location, is_bookable, status, brand, model, vendor, warranty_expiry, custom_values, created_by) VALUES
  ('AF-0001', 'Dell Latitude 5440',             1, 'SN-DL-2201', '2025-01-15', 85000,  'good', 'HQ Floor 2',              false, 'allocated',         'Dell',      'Latitude 5440',       'Dell India',       '2027-01-15', '{"RAM (GB)":16,"OS":"Windows 11 Pro"}', 2),
  ('AF-0002', 'MacBook Air M3',                 1, 'SN-MB-1102', '2025-04-10', 114900, 'new',  'HQ Floor 2',              false, 'available',         'Apple',     'MacBook Air 13 M3',   'Apple Authorized', '2027-04-10', '{"RAM (GB)":16,"OS":"macOS Sonoma"}', 2),
  ('AF-0003', 'Room B2',                        2, NULL,         NULL,         NULL,   'good', 'HQ Floor 1',              true,  'available',         'AssetHub',  'Conference 8-Seater', NULL,               NULL,         '{"Capacity":8}',                       2),
  ('AF-0004', 'Executive Boardroom Alpha',      2, NULL,         NULL,         NULL,   'good', 'HQ Floor 4 Executive',    true,  'available',         'AssetHub',  'Boardroom 20-Seater', NULL,               NULL,         '{"Capacity":20}',                      2),
  ('AF-0005', 'MacBook Pro 16" M3 Max',         1, 'SN-MB-9941', '2025-03-01', 289900, 'new',  'HQ Floor 3 Engineering',  false, 'allocated',         'Apple',     'MacBook Pro 16"',     'Apple Authorized', '2028-03-01', '{"RAM (GB)":36,"OS":"macOS Sonoma"}', 2),
  ('AF-0006', 'Dell UltraSharp 32" 4K Monitor', 4, 'SN-DS-4410', '2025-02-18', 64500,  'good', 'HQ Floor 3 Engineering',  false, 'allocated',         'Dell',      'U3223QE',             'CompServ',         '2028-02-18', '{"Screen Size":"32 inch"}',            2),
  ('AF-0007', 'Apple Studio Display 27" 5K',    4, 'SN-AS-8812', '2025-05-12', 159900, 'new',  'HQ Floor 2 Design Studio',false, 'allocated',         'Apple',     'Studio Display',      'Apple Authorized', '2027-05-12', '{"Screen Size":"27 inch"}',            2),
  ('AF-0008', 'ThinkPad X1 Carbon Gen 11',      1, 'SN-TP-3319', '2024-11-20', 142000, 'good', 'HQ Floor 1 Operations',   false, 'allocated',         'Lenovo',    'X1 Carbon Gen 11',    'Lenovo Direct',    '2026-11-20', '{"RAM (GB)":16,"OS":"Fedora Linux"}',   2),
  ('AF-0009', 'iPad Pro 12.9" M2 with Pencil',  5, 'SN-IP-7714', '2025-02-05', 112000, 'good', 'HQ Floor 2 Design Studio',false, 'available',         'Apple',     'iPad Pro 12.9" M2',   'Apple Authorized', '2027-02-05', '{"Storage":"256 GB"}',                 2),
  ('AF-0010', 'Sony 4K Laser Cinema Projector', 2, 'SN-SN-1092', '2024-09-10', 215000, 'good', 'HQ Auditorium Floor 1',  true,  'available',         'Sony',      'VPL-XW5000ES',        'Sony Pro AV',      '2026-09-10', '{"Capacity":120}',                     2),
  ('AF-0011', 'Mahindra XUV700 Executive EV',   3, 'KA-01-EQ-9090', '2024-06-15', 2450000, 'good', 'HQ Basement Bay 03', true,  'available',         'Mahindra',  'XUV700 EV Shuttle',   'Sireesh Auto',     '2029-06-15', '{"Fuel Type":"Electric"}',             2),
  ('AF-0012', 'HP Color LaserJet Enterprise',   1, 'SN-HP-5521', '2023-08-14', 78000,  'fair', 'HQ Floor 2 Print Room',   false, 'under_maintenance', 'HP',        'Color LaserJet M554', 'HP Direct',        '2025-08-14', '{"RAM (GB)":2,"OS":"Embedded Linux"}', 2),
  ('AF-0013', 'MacBook Air M2 (Damaged Screen)',1, 'SN-MB-2219', '2024-01-20', 99900,  'poor', 'IT Bay Diagnostic Desk',  false, 'under_maintenance', 'Apple',     'MacBook Air 13 M2',   'Apple Authorized', '2026-01-20', '{"RAM (GB)":8,"OS":"macOS Ventura"}', 2),
  ('AF-0014', 'Dell PowerEdge R750 Rack Server',1, 'SN-PE-0901', '2024-03-30', 485000, 'good', 'Server Room Rack 04',    false, 'reserved',          'Dell',      'PowerEdge R750',      'Dell Enterprise',  '2029-03-30', '{"RAM (GB)":128,"OS":"Ubuntu Server"}',2),
  ('AF-0015', 'Logitech Rally Plus AV System',  2, 'SN-LG-6623', '2024-10-18', 185000, 'good', 'HQ Floor 4 Executive',    false, 'available',         'Logitech',  'Rally Plus Ultra-HD', 'Logi India',       '2026-10-18', '{"Capacity":16}',                      2);

-- 6. Allocations (Active & Historical)
-- Active: Asset 1 (Dell) to Priya Sharma (user 4)
INSERT INTO allocations (asset_id, employee_id, allocated_by, allocated_at, expected_return_date, status)
VALUES (1, 4, 2, now() - INTERVAL '14' DAY, CURRENT_DATE + 14, 'active');

-- Active: Asset 5 (MacBook Pro 16") to Aarav Patel (user 7)
INSERT INTO allocations (asset_id, employee_id, allocated_by, allocated_at, expected_return_date, status)
VALUES (5, 7, 2, now() - INTERVAL '30' DAY, CURRENT_DATE + 60, 'active');

-- Active: Asset 6 (Dell 4K Monitor) to Aarav Patel (user 7)
INSERT INTO allocations (asset_id, employee_id, allocated_by, allocated_at, expected_return_date, status)
VALUES (6, 7, 2, now() - INTERVAL '25' DAY, CURRENT_DATE + 65, 'active');

-- Active: Asset 7 (Studio Display 27") to Sneha Rao (user 8)
INSERT INTO allocations (asset_id, employee_id, allocated_by, allocated_at, expected_return_date, status)
VALUES (7, 8, 2, now() - INTERVAL '20' DAY, CURRENT_DATE + 40, 'active');

-- Active: Asset 8 (ThinkPad X1) to Rajesh Kumar (user 5)
INSERT INTO allocations (asset_id, employee_id, allocated_by, allocated_at, expected_return_date, status)
VALUES (8, 5, 2, now() - INTERVAL '45' DAY, CURRENT_DATE + 15, 'active');

-- Historical returned: Asset 2 (MacBook Air) was held by Harish Verma and returned
INSERT INTO allocations (asset_id, employee_id, allocated_by, allocated_at, returned_at, return_notes, status)
VALUES (2, 3, 2, now() - INTERVAL '90' DAY, now() - INTERVAL '10' DAY, 'Sprint complete; machine thoroughly inspected and returned.', 'returned');

-- 7. Transfer Requests (Demonstrates asset transfer workflow)
INSERT INTO transfer_requests (asset_id, from_allocation_id, requested_by, to_employee_id, reason, status)
VALUES (8, 5, 3, 7, 'Employee transferred to Engineering core team; requires laptop setup immediately.', 'pending');

-- 8. Bookings (Demonstrates Upcoming, Ongoing, Approved, Pending)
-- Ongoing / Today: Room B2 (Asset 3) booked by Priya Sharma
INSERT INTO bookings (asset_id, booked_by, start_time, end_time, purpose, status)
VALUES (3, 4, now() - INTERVAL '1' HOUR, now() + INTERVAL '2' HOUR, 'Sprint Planning & Release Demo', 'approved');

-- Upcoming tomorrow: Executive Boardroom (Asset 4) booked by Vikram Malhotra
INSERT INTO bookings (asset_id, booked_by, start_time, end_time, purpose, status)
VALUES (4, 9, now() + INTERVAL '1' DAY, now() + INTERVAL '1' DAY + INTERVAL '3' HOUR, 'Quarterly Board & Investor Briefing', 'approved');

-- Upcoming pending: Mahindra EV Shuttle (Asset 11) requested by Rajesh Kumar
INSERT INTO bookings (asset_id, booked_by, start_time, end_time, purpose, status)
VALUES (11, 5, now() + INTERVAL '2' DAY + INTERVAL '4' HOUR, now() + INTERVAL '2' DAY + INTERVAL '8' HOUR, 'Client Onsite Visit to Tech Park', 'pending');

-- Upcoming approved: Sony 4K Projector (Asset 10) booked by Sneha Rao
INSERT INTO bookings (asset_id, booked_by, start_time, end_time, purpose, status)
VALUES (10, 8, now() + INTERVAL '3' DAY, now() + INTERVAL '3' DAY + INTERVAL '4' HOUR, 'Brand Design & Design System Showcase', 'approved');

-- 9. Maintenance Requests (Demonstrates state transitions: in_progress, assigned, resolved)
-- In-progress ticket with Alex
INSERT INTO maintenance_requests (asset_id, raised_by, issue, priority, status, technician, technician_id, decided_by, decided_at)
VALUES (12, 4, 'Paper feed roller jammed repeatedly during duplex print jobs.', 'high', 'in_progress', 'Alex Miller', 1, 2, now() - INTERVAL '2' DAY);

-- Assigned ticket
INSERT INTO maintenance_requests (asset_id, raised_by, issue, priority, status, technician, technician_id, decided_by, decided_at)
VALUES (13, 5, 'Retina display cracked along bottom bezel; flickering rainbow artifacts.', 'critical', 'assigned', 'Alex Miller', 1, 2, now() - INTERVAL '1' DAY);

-- Resolved ticket
INSERT INTO maintenance_requests (asset_id, raised_by, issue, priority, status, technician, technician_id, decided_by, decided_at, resolved_at)
VALUES (6, 7, 'Loose DisplayPort cable causing sporadic black screen flickers.', 'medium', 'resolved', 'David Chen', 2, 2, now() - INTERVAL '10' DAY, now() - INTERVAL '2' DAY);

-- 10. Audit Cycles & Records
INSERT INTO audit_cycles (name, scope_department_id, scope_location, start_date, end_date, status, created_by)
VALUES ('Q4 2026 Annual IT Hardware & Infrastructure Audit', 1, 'HQ All Floors', CURRENT_DATE - 5, CURRENT_DATE + 25, 'open', 2);

-- Assign Priya & Aarav as auditors
INSERT INTO audit_assignments (cycle_id, auditor_id) VALUES (1, 4), (1, 7);

-- Audit records inside Cycle 1
INSERT INTO audit_records (cycle_id, asset_id, result, notes, audited_by, audited_at)
VALUES 
  (1, 1, 'verified', 'Asset in daily use by Priya; physical condition confirmed as Good.', 4, now() - INTERVAL '3' DAY),
  (1, 13, 'damaged', 'Logged display damage, ticket #2 raised for screen assembly replacement.', 4, now() - INTERVAL '2' DAY),
  (1, 5, 'verified', 'MacBook Pro verified at Engineering desk 3B.', 7, now() - INTERVAL '1' DAY);

-- Closed previous cycle for historical data in Reports
INSERT INTO audit_cycles (name, scope_department_id, scope_location, start_date, end_date, status, created_by, closed_at)
VALUES ('Q3 2026 Operations & Facility Equipment Audit', 2, 'HQ Floor 1 & Basement', CURRENT_DATE - 90, CURRENT_DATE - 60, 'closed', 2, now() - INTERVAL '60' DAY);

INSERT INTO audit_assignments (cycle_id, auditor_id) VALUES (2, 5);

INSERT INTO audit_records (cycle_id, asset_id, result, notes, audited_by, audited_at)
VALUES (2, 11, 'verified', 'Shuttle odometer & charging unit inspected.', 5, now() - INTERVAL '65' DAY);

-- 11. Notifications
INSERT INTO notifications (user_id, type, message, read, created_at) VALUES
  (4, 'asset_assigned', 'Dell Latitude 5440 (AF-0001) has been assigned to you', false, now() - INTERVAL '14' DAY),
  (7, 'asset_assigned', 'MacBook Pro 16" M3 Max (AF-0005) has been assigned to you', true, now() - INTERVAL '30' DAY),
  (8, 'asset_assigned', 'Apple Studio Display 27" 5K (AF-0007) has been assigned to you', false, now() - INTERVAL '20' DAY),
  (4, 'audit_assigned', 'You have been assigned as an auditor for: Q4 2026 Annual IT Hardware & Infrastructure Audit', false, now() - INTERVAL '5' DAY),
  (7, 'audit_assigned', 'You have been assigned as an auditor for: Q4 2026 Annual IT Hardware & Infrastructure Audit', true, now() - INTERVAL '5' DAY),
  (2, 'booking_requested', 'New booking request pending your approval: Mahindra XUV700 Executive EV', false, now() - INTERVAL '2' HOUR);

-- 12. Activity Logs (Feeds the Dashboard activity stream)
INSERT INTO activity_logs (user_id, action, entity_type, entity_id, details, created_at) VALUES
  (2, 'asset.created', 'asset', 1, 'Created asset AF-0001 (Dell Latitude 5440)', now() - INTERVAL '15' DAY),
  (2, 'allocation.created', 'asset', 1, 'AF-0001 allocated to Priya Sharma', now() - INTERVAL '14' DAY),
  (2, 'asset.created', 'asset', 5, 'Created asset AF-0005 (MacBook Pro 16" M3 Max)', now() - INTERVAL '31' DAY),
  (2, 'allocation.created', 'asset', 5, 'AF-0005 allocated to Aarav Patel', now() - INTERVAL '30' DAY),
  (4, 'booking.created', 'asset', 3, 'Room B2 (Sprint Planning & Release Demo)', now() - INTERVAL '1' DAY),
  (2, 'booking.approved', 'booking', 1, 'Approved booking for Room B2', now() - INTERVAL '18' HOUR),
  (4, 'maintenance.created', 'asset', 12, 'Raised ticket: Paper feed roller jammed', now() - INTERVAL '2' DAY),
  (2, 'maintenance.assigned', 'maintenance', 1, 'Assigned ticket #1 to Alex Miller', now() - INTERVAL '2' DAY),
  (2, 'audit.created', 'audit_cycle', 1, 'Created cycle: Q4 2026 Annual IT Hardware Audit', now() - INTERVAL '5' DAY),
  (4, 'audit.recorded', 'asset', 1, 'Audited AF-0001: verified', now() - INTERVAL '3' DAY);
