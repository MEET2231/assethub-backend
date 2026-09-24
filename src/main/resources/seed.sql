-- AssetHub demo data (matching AssetFlow)
-- Passwords: admin@assetflow.com / admin123 | others / password123

INSERT INTO departments (name) VALUES ('IT'), ('Operations');

INSERT INTO users (name, email, password_hash, role, department_id) VALUES
  ('Admin',         'admin@assetflow.com',   '$2a$10$wIoFXaCuLL.qV1qRL6VSZONLMjUTa49/mw9CnWqFZ0ykn7NWpt9V2', 'admin',         NULL),
  ('Meera Manager', 'manager@assetflow.com', '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'asset_manager', 1),
  ('Harish Head',   'head@assetflow.com',    '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'dept_head',     2),
  ('Priya',         'priya@assetflow.com',   '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'employee',      1),
  ('Raj',           'raj@assetflow.com',     '$2a$10$atcbY4AczBdO7rYmvumdpOQzCkaG5mfbdmaxaCphIA.IlRkI09.bi', 'employee',      2);

UPDATE departments SET head_id = 3 WHERE id = 2;

INSERT INTO categories (name, description, custom_fields) VALUES
  ('Electronics',   'Laptops, monitors, phones', '[{"name":"Warranty (months)","type":"number"}]'),
  ('Meeting Rooms', 'Bookable shared spaces',    '[]'),
  ('Vehicles',      'Company vehicles',          '[]');

INSERT INTO assets (asset_tag, name, category_id, serial_number, acquisition_date, acquisition_cost, condition, location, is_bookable, status, created_by) VALUES
  ('AF-0001', 'Dell Latitude 5440', 1, 'SN-DL-2201', '2025-01-15', 85000, 'good', 'HQ Floor 2', false, 'allocated', 2),
  ('AF-0002', 'MacBook Air M3',     1, 'SN-MB-1102', NULL,         NULL,  'new',  'HQ Floor 2', false, 'available', 2),
  ('AF-0003', 'Room B2',            2, NULL,         NULL,         NULL,  'good', 'HQ Floor 1', true,  'available', 2);

-- Priya holds the Dell laptop, due back in 14 days
INSERT INTO allocations (asset_id, employee_id, allocated_by, expected_return_date)
VALUES (1, 4, 2, CURRENT_DATE + 14);

INSERT INTO notifications (user_id, type, message)
SELECT 4, 'asset_assigned', 'Dell Latitude 5440 (AF-0001) has been assigned to you'
WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = 4 AND type = 'asset_assigned');

INSERT INTO activity_logs (user_id, action, entity_type, entity_id, details)
SELECT 2, 'allocation.created', 'asset', 1, 'AF-0001 allocated to Priya'
WHERE NOT EXISTS (SELECT 1 FROM activity_logs WHERE action = 'allocation.created' AND entity_id = 1);
