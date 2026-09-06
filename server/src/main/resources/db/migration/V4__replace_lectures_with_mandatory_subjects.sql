-- Mandatory subjects replace the former class-session "lectures" table.
-- The table names make the model clear: a mandatory subject is offered for
-- one or more grades, and its learning units continue to hold the content.

DROP TABLE IF EXISTS lectures;

ALTER TABLE subjects RENAME TO mandatory_subjects;
ALTER TABLE subject_grade_levels RENAME TO mandatory_subject_grade_levels;

ALTER TABLE mandatory_subjects RENAME COLUMN subject_id TO mandatory_subject_id;
ALTER TABLE mandatory_subject_grade_levels RENAME COLUMN subject_id TO mandatory_subject_id;
ALTER TABLE lecture_units RENAME COLUMN subject_id TO mandatory_subject_id;

ALTER INDEX IF EXISTS idx_lecture_units_subject_order
    RENAME TO idx_lecture_units_mandatory_subject_order;

-- One subject can be mandatory in several grades. Insert the approved
-- grade/subject list in a later migration after it has been provided.
