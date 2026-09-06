-- V1 already created the courses catalogue. Do not drop or recreate it here.
-- A "lecture" is mandatory for everyone enrolled in its class.
-- A "course" is elective: children choose from the courses offered to their class.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE user_role AS ENUM ('ADMIN', 'TEACHER', 'PARENT', 'SUPPORT_AGENT');
CREATE TYPE gender_type AS ENUM ('MALE', 'FEMALE');
CREATE TYPE attendance_status AS ENUM ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED');

CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE teacher_profiles (
    teacher_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    qualification VARCHAR(255),
    specialization VARCHAR(100),
    years_of_experience INTEGER NOT NULL DEFAULT 0 CHECK (years_of_experience >= 0),
    hire_date DATE
);

CREATE TABLE children (
    child_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    preferred_name VARCHAR(100),
    date_of_birth DATE NOT NULL,
    gender gender_type NOT NULL,
    allergies TEXT,
    medical_conditions TEXT,
    dietary_restrictions TEXT,
    emergency_notes TEXT,
    grade_level SMALLINT CHECK (grade_level BETWEEN 1 AND 9),
    enrollment_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE parent_child_relationships (
    relationship_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parent_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    child_id UUID NOT NULL REFERENCES children(child_id) ON DELETE CASCADE,
    relationship_type VARCHAR(50) NOT NULL,
    is_primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    can_pickup BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_parent_child UNIQUE (parent_id, child_id)
);

-- A class is the child's main group, for example "Grade 5 Blue, 2026".
CREATE TABLE classes (
    class_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    class_name VARCHAR(100) NOT NULL,
    grade_level SMALLINT NOT NULL CHECK (grade_level BETWEEN 1 AND 9),
    academic_year VARCHAR(20) NOT NULL,
    teacher_id UUID REFERENCES teacher_profiles(teacher_id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_class_per_year UNIQUE (class_name, academic_year)
);

CREATE TABLE class_enrollments (
    enrollment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    class_id UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    child_id UUID NOT NULL REFERENCES children(child_id) ON DELETE CASCADE,
    enrolled_at DATE NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT unique_class_child UNIQUE (class_id, child_id)
);

-- Lectures are required sessions for every child in the class.
CREATE TABLE lectures (
    lecture_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    class_id UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    scheduled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Courses were created in V1. They are the elective-course catalogue.
ALTER TABLE courses ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- A course offering makes one catalogue course selectable by one class.
CREATE TABLE course_offerings (
    offering_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    class_id UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    term VARCHAR(50) NOT NULL,
    capacity INTEGER CHECK (capacity IS NULL OR capacity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_course_offering UNIQUE (class_id, course_id, term)
);

-- The rule that says how many elective courses each child must choose.
CREATE TABLE class_elective_requirements (
    class_id UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    term VARCHAR(50) NOT NULL,
    minimum_courses INTEGER NOT NULL CHECK (minimum_courses >= 0),
    PRIMARY KEY (class_id, term)
);

-- One row means one child selected one offered elective course.
CREATE TABLE child_course_enrollments (
    enrollment_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    child_id UUID NOT NULL REFERENCES children(child_id) ON DELETE CASCADE,
    offering_id UUID NOT NULL REFERENCES course_offerings(offering_id) ON DELETE CASCADE,
    selected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_child_course_selection UNIQUE (child_id, offering_id)
);

CREATE TABLE child_attendance (
    attendance_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    child_id UUID NOT NULL REFERENCES children(child_id) ON DELETE CASCADE,
    date DATE NOT NULL,
    status attendance_status NOT NULL,
    check_in_time TIMESTAMPTZ,
    check_out_time TIMESTAMPTZ,
    remarks TEXT,
    logged_by UUID REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT unique_child_daily_attendance UNIQUE (child_id, date)
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_children_name ON children(last_name, first_name);
CREATE INDEX idx_class_enrollments_child ON class_enrollments(child_id);
CREATE INDEX idx_lectures_class ON lectures(class_id);
CREATE INDEX idx_course_offerings_class_term ON course_offerings(class_id, term);
CREATE INDEX idx_child_course_enrollments_child ON child_course_enrollments(child_id);
