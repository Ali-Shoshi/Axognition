-- Use one clear language-subject label in every supported language.
UPDATE mandatory_subjects
SET
    name_english = 'Albanian Language',
    name_albanian = 'Gjuhë Shqipe',
    name_serbian = 'Albanski jezik'
WHERE name_english = 'Albanian Language / Mother Tongue';
