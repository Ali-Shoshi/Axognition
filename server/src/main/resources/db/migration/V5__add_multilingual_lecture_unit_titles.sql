-- Each mandatory-subject learning unit can be presented in all supported languages.
-- The former title column already holds the English title.

ALTER TABLE lecture_units
    RENAME COLUMN title TO title_english;

ALTER TABLE lecture_units
    ADD COLUMN title_albanian VARCHAR(200),
    ADD COLUMN title_serbian VARCHAR(200);
