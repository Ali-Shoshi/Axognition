-- Serbian translations for the mandatory-subject catalogue.
UPDATE mandatory_subjects
SET name_serbian = CASE name_english
    WHEN 'Albanian Language / Mother Tongue' THEN 'Albanski jezik / maternji jezik'
    WHEN 'Mathematics' THEN 'Matematika'
    WHEN 'Man and Nature' THEN 'Čovek i priroda'
    WHEN 'Society and Environment' THEN 'Društvo i životna sredina'
    WHEN 'Visual Arts' THEN 'Likovna kultura'
    WHEN 'Music Education' THEN 'Muzička kultura'
    WHEN 'Physical Education, Sports and Health' THEN 'Fizičko vaspitanje, sport i zdravlje'
    WHEN 'Life Skills / Life and Work' THEN 'Životne veštine / Život i rad'
    WHEN 'English Language' THEN 'Engleski jezik'
    WHEN 'Information and Communication Technology (ICT)' THEN 'Informaciona i komunikaciona tehnologija (IKT)'
END
WHERE name_english IN (
    'Albanian Language / Mother Tongue',
    'Mathematics',
    'Man and Nature',
    'Society and Environment',
    'Visual Arts',
    'Music Education',
    'Physical Education, Sports and Health',
    'Life Skills / Life and Work',
    'English Language',
    'Information and Communication Technology (ICT)'
);
