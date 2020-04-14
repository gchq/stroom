-------------------------------------------------------------------------
-- Delete redundant seed data
-------------------------------------------------------------------------

DELETE FROM tokens
WHERE user_id = (SELECT id FROM users WHERE email = 'stroomServiceUser');

DELETE FROM tokens
WHERE user_id = (SELECT id FROM users WHERE email = 'statsServiceUser');

DELETE FROM users WHERE email = 'stroomServiceUser';

DELETE FROM users WHERE email = 'statsServiceUser';
