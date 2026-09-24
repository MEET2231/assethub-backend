-- AssetHub schema (matching AssetFlow)

CREATE TABLE IF NOT EXISTS departments (
  id          SERIAL PRIMARY KEY,
  name        TEXT NOT NULL UNIQUE,
  head_id     INTEGER,
  parent_id   INTEGER REFERENCES departments(id),
  status      TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS users (
  id            SERIAL PRIMARY KEY,
  name          TEXT NOT NULL,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'employee' CHECK (role IN ('admin','asset_manager','dept_head','employee')),
  department_id INTEGER REFERENCES departments(id),
  status        TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS categories (
  id            SERIAL PRIMARY KEY,
  name          TEXT NOT NULL UNIQUE,
  description   TEXT,
  custom_fields JSON NOT NULL DEFAULT '[]',
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS assets (
  id               SERIAL PRIMARY KEY,
  asset_tag        TEXT NOT NULL UNIQUE,
  name             TEXT NOT NULL,
  category_id      INTEGER NOT NULL REFERENCES categories(id),
  serial_number    TEXT,
  acquisition_date DATE,
  acquisition_cost NUMERIC(12,2),
  condition        TEXT NOT NULL DEFAULT 'good' CHECK (condition IN ('new','good','fair','poor')),
  location         TEXT,
  image_url        TEXT,
  brand            TEXT,
  model            TEXT,
  vendor           TEXT,
  warranty_expiry  DATE,
  is_bookable      BOOLEAN NOT NULL DEFAULT false,
  status           TEXT NOT NULL DEFAULT 'available'
                   CHECK (status IN ('available','allocated','reserved','under_maintenance','lost','retired','disposed')),
  custom_values    JSON NOT NULL DEFAULT '{}',
  created_by       INTEGER REFERENCES users(id),
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS allocations (
  id                   SERIAL PRIMARY KEY,
  asset_id             INTEGER NOT NULL REFERENCES assets(id),
  employee_id          INTEGER REFERENCES users(id),
  department_id        INTEGER REFERENCES departments(id),
  allocated_by         INTEGER NOT NULL REFERENCES users(id),
  allocated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  expected_return_date DATE,
  returned_at          TIMESTAMP WITH TIME ZONE,
  return_notes         TEXT,
  status               TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','returned')),
  CHECK (employee_id IS NOT NULL OR department_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_allocations_asset_active ON allocations(asset_id, status);

CREATE TABLE IF NOT EXISTS transfer_requests (
  id                 SERIAL PRIMARY KEY,
  asset_id           INTEGER NOT NULL REFERENCES assets(id),
  from_allocation_id INTEGER NOT NULL REFERENCES allocations(id),
  requested_by       INTEGER NOT NULL REFERENCES users(id),
  to_employee_id     INTEGER REFERENCES users(id),
  to_department_id   INTEGER REFERENCES departments(id),
  reason             TEXT,
  status             TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
  decided_by         INTEGER REFERENCES users(id),
  decided_at         TIMESTAMP WITH TIME ZONE,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bookings (
  id         SERIAL PRIMARY KEY,
  asset_id   INTEGER NOT NULL REFERENCES assets(id),
  booked_by  INTEGER NOT NULL REFERENCES users(id),
  start_time TIMESTAMP WITH TIME ZONE NOT NULL,
  end_time   TIMESTAMP WITH TIME ZONE NOT NULL,
  purpose    TEXT,
  status     TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected','cancelled')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CHECK (end_time > start_time)
);

CREATE TABLE IF NOT EXISTS technicians (
  id          SERIAL PRIMARY KEY,
  name        TEXT NOT NULL,
  email       TEXT UNIQUE,
  phone       TEXT,
  specialty   TEXT,
  status      TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS maintenance_requests (
  id             SERIAL PRIMARY KEY,
  asset_id       INTEGER NOT NULL REFERENCES assets(id),
  raised_by      INTEGER NOT NULL REFERENCES users(id),
  issue          TEXT NOT NULL,
  priority       TEXT NOT NULL DEFAULT 'medium' CHECK (priority IN ('low','medium','high','critical')),
  photo_url      TEXT,
  status         TEXT NOT NULL DEFAULT 'pending'
                 CHECK (status IN ('pending','approved','rejected','assigned','in_progress','resolved')),
  technician     TEXT,
  technician_id  INTEGER REFERENCES technicians(id),
  decided_by     INTEGER REFERENCES users(id),
  decided_at     TIMESTAMP WITH TIME ZONE,
  resolved_at    TIMESTAMP WITH TIME ZONE,
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_cycles (
  id                  SERIAL PRIMARY KEY,
  name                TEXT NOT NULL,
  scope_department_id INTEGER REFERENCES departments(id),
  scope_location      TEXT,
  start_date          DATE NOT NULL,
  end_date            DATE NOT NULL,
  status              TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','closed')),
  created_by          INTEGER NOT NULL REFERENCES users(id),
  closed_at           TIMESTAMP WITH TIME ZONE,
  created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_assignments (
  cycle_id   INTEGER NOT NULL REFERENCES audit_cycles(id) ON DELETE CASCADE,
  auditor_id INTEGER NOT NULL REFERENCES users(id),
  PRIMARY KEY (cycle_id, auditor_id)
);

CREATE TABLE IF NOT EXISTS audit_records (
  id         SERIAL PRIMARY KEY,
  cycle_id   INTEGER NOT NULL REFERENCES audit_cycles(id),
  asset_id   INTEGER NOT NULL REFERENCES assets(id),
  result     TEXT NOT NULL CHECK (result IN ('verified','missing','damaged')),
  notes      TEXT,
  audited_by INTEGER NOT NULL REFERENCES users(id),
  audited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  UNIQUE (cycle_id, asset_id)
);

CREATE TABLE IF NOT EXISTS notifications (
  id         SERIAL PRIMARY KEY,
  user_id    INTEGER NOT NULL REFERENCES users(id),
  type       TEXT NOT NULL,
  message    TEXT NOT NULL,
  read       BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS activity_logs (
  id          SERIAL PRIMARY KEY,
  user_id     INTEGER REFERENCES users(id),
  action      TEXT NOT NULL,
  entity_type TEXT,
  entity_id   INTEGER,
  details     TEXT,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_assets_status ON assets(status);
CREATE INDEX IF NOT EXISTS idx_bookings_asset_time ON bookings(asset_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, read);
CREATE INDEX IF NOT EXISTS idx_allocations_asset ON allocations(asset_id);
