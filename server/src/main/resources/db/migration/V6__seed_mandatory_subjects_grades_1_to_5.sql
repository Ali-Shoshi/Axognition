-- Store subject labels in the same three-language structure as lecture units.
-- Serbian values are intentionally NULL until approved translations are supplied.
ALTER TABLE mandatory_subjects
    RENAME COLUMN name TO name_english;

ALTER TABLE mandatory_subjects
    ADD COLUMN name_albanian VARCHAR(100),
    ADD COLUMN name_serbian VARCHAR(100);

INSERT INTO mandatory_subjects (name_english, name_albanian) VALUES
    ('Albanian Language / Mother Tongue', 'Gjuhë Shqipe / Gjuhë Amtare'),
    ('Mathematics', 'Matematikë'),
    ('Man and Nature', 'Njeriu dhe Natyra'),
    ('Society and Environment', 'Shoqëria dhe Mjedisi'),
    ('Visual Arts', 'Edukatë Figurative'),
    ('Music Education', 'Edukatë Muzikore'),
    ('Physical Education, Sports and Health', 'Edukatë Fizike, Sportet dhe Shëndeti'),
    ('Life Skills / Life and Work', 'Shkathtësi për Jetë / Jeta dhe Puna'),
    ('English Language', 'Gjuhë Angleze'),
    ('Information and Communication Technology (ICT)', 'Teknologji e Informacionit dhe e Komunikimit - TIK')
ON CONFLICT (name_english) DO UPDATE
    SET name_albanian = EXCLUDED.name_albanian;

-- Approved mandatory-subject list for Grades 1-5.
INSERT INTO mandatory_subject_grade_levels (mandatory_subject_id, grade_level)
SELECT subject.mandatory_subject_id, approved.grade_level
FROM (
    VALUES
        ('Albanian Language / Mother Tongue', 1),
        ('Mathematics', 1),
        ('Man and Nature', 1),
        ('Society and Environment', 1),
        ('Visual Arts', 1),
        ('Music Education', 1),
        ('Physical Education, Sports and Health', 1),
        ('Life Skills / Life and Work', 1),

        ('Albanian Language / Mother Tongue', 2),
        ('Mathematics', 2),
        ('Man and Nature', 2),
        ('Society and Environment', 2),
        ('Visual Arts', 2),
        ('Music Education', 2),
        ('Physical Education, Sports and Health', 2),
        ('Life Skills / Life and Work', 2),

        ('Albanian Language / Mother Tongue', 3),
        ('English Language', 3),
        ('Mathematics', 3),
        ('Man and Nature', 3),
        ('Society and Environment', 3),
        ('Visual Arts', 3),
        ('Music Education', 3),
        ('Physical Education, Sports and Health', 3),
        ('Life Skills / Life and Work', 3),

        ('Albanian Language / Mother Tongue', 4),
        ('English Language', 4),
        ('Mathematics', 4),
        ('Man and Nature', 4),
        ('Society and Environment', 4),
        ('Information and Communication Technology (ICT)', 4),
        ('Visual Arts', 4),
        ('Music Education', 4),
        ('Physical Education, Sports and Health', 4),
        ('Life Skills / Life and Work', 4),

        ('Albanian Language / Mother Tongue', 5),
        ('English Language', 5),
        ('Mathematics', 5),
        ('Man and Nature', 5),
        ('Society and Environment', 5),
        ('Information and Communication Technology (ICT)', 5),
        ('Visual Arts', 5),
        ('Music Education', 5),
        ('Physical Education, Sports and Health', 5),
        ('Life Skills / Life and Work', 5)
) AS approved(name_english, grade_level)
JOIN mandatory_subjects AS subject ON subject.name_english = approved.name_english
ON CONFLICT (mandatory_subject_id, grade_level) DO NOTHING;
