-- File bytes live in private object storage (for example Google Cloud Storage).
-- These tables store only metadata and the storage object keys.

CREATE TYPE book_category AS ENUM (
    'LECTURES',
    'COURSES',
    'REGULAR',
    'GENERAL'
);

CREATE TYPE book_format AS ENUM ('PDF', 'EPUB', 'OTHER');
CREATE TYPE video_category AS ENUM ('LECTURE', 'COURSE');

-- The platform supports Grades 1 through 9 only.
CREATE TABLE grade_levels (
    grade_level SMALLINT PRIMARY KEY CHECK (grade_level BETWEEN 1 AND 9),
    display_name VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO grade_levels (grade_level, display_name) VALUES
    (1, 'Grade 1'),
    (2, 'Grade 2'),
    (3, 'Grade 3'),
    (4, 'Grade 4'),
    (5, 'Grade 5'),
    (6, 'Grade 6'),
    (7, 'Grade 7'),
    (8, 'Grade 8'),
    (9, 'Grade 9');

ALTER TABLE children
    ADD CONSTRAINT children_grade_level_exists
    FOREIGN KEY (grade_level) REFERENCES grade_levels(grade_level);

ALTER TABLE classes
    ADD CONSTRAINT classes_grade_level_exists
    FOREIGN KEY (grade_level) REFERENCES grade_levels(grade_level);

-- The subject-based lecture area displayed in Lectures.kt.
CREATE TABLE subjects (
    subject_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A subject can be available in many grades, for example Mathematics in Grades 1-9.
CREATE TABLE subject_grade_levels (
    subject_id UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    grade_level SMALLINT NOT NULL REFERENCES grade_levels(grade_level) ON DELETE RESTRICT,
    PRIMARY KEY (subject_id, grade_level)
);

CREATE TABLE lecture_units (
    lecture_unit_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_id UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    grade_level SMALLINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_lecture_unit_title UNIQUE (subject_id, grade_level, title),
    CONSTRAINT lecture_unit_subject_grade_exists
        FOREIGN KEY (subject_id, grade_level)
        REFERENCES subject_grade_levels(subject_id, grade_level)
        ON DELETE CASCADE
);

-- The elective-course area displayed in Courses.kt. The course catalogue is from V1.
CREATE TABLE course_units (
    course_unit_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_course_unit_title UNIQUE (course_id, title)
);

-- Four book collections exactly matching Books.kt:
-- LECTURES, COURSES, REGULAR (Assigned Reading), and GENERAL (General Books).
CREATE TABLE books (
    book_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(300) NOT NULL,
    author VARCHAR(200),
    description TEXT,
    category book_category NOT NULL,
    format book_format NOT NULL,
    storage_object_key TEXT NOT NULL UNIQUE,
    cover_object_key TEXT,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes >= 0),
    checksum_sha256 VARCHAR(64),
    is_downloadable BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    lecture_unit_id UUID REFERENCES lecture_units(lecture_unit_id) ON DELETE SET NULL,
    course_id BIGINT REFERENCES courses(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_book_category_link CHECK (
        (category = 'LECTURES' AND lecture_unit_id IS NOT NULL AND course_id IS NULL)
        OR (category = 'COURSES' AND course_id IS NOT NULL AND lecture_unit_id IS NULL)
        OR (category IN ('REGULAR', 'GENERAL') AND lecture_unit_id IS NULL AND course_id IS NULL)
    )
);

-- One video table, divided into the two UI categories: lecture videos and course videos.
CREATE TABLE videos (
    video_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(300) NOT NULL,
    description TEXT,
    category video_category NOT NULL,
    lecture_unit_id UUID REFERENCES lecture_units(lecture_unit_id) ON DELETE CASCADE,
    course_unit_id UUID REFERENCES course_units(course_unit_id) ON DELETE CASCADE,
    stream_manifest_object_key TEXT NOT NULL UNIQUE,
    download_object_key TEXT,
    thumbnail_object_key TEXT,
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds > 0),
    file_size_bytes BIGINT CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0),
    is_downloadable BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_video_category_link CHECK (
        (category = 'LECTURE' AND lecture_unit_id IS NOT NULL AND course_unit_id IS NULL)
        OR (category = 'COURSE' AND course_unit_id IS NOT NULL AND lecture_unit_id IS NULL)
    )
);

CREATE INDEX idx_lecture_units_subject_order ON lecture_units(subject_id, display_order);
CREATE INDEX idx_lecture_units_grade ON lecture_units(grade_level, display_order);
CREATE INDEX idx_course_units_course_order ON course_units(course_id, display_order);
CREATE INDEX idx_books_category ON books(category);
CREATE INDEX idx_books_lecture_unit ON books(lecture_unit_id);
CREATE INDEX idx_books_course ON books(course_id);
CREATE INDEX idx_videos_lecture_unit_order ON videos(lecture_unit_id, display_order);
CREATE INDEX idx_videos_course_unit_order ON videos(course_unit_id, display_order);
