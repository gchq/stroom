-- Since moving auth into stroom we no longer need these users

DELETE FROM tokens WHERE user_id=(SELECT id FROM users WHERE email="stroomServiceUser");
DELETE FROM users WHERE email='stroomServiceUser';

DELETE FROM tokens WHERE user_id=(SELECT id FROM users WHERE email="statsServiceUser");
DELETE FROM users WHERE email='statsServiceUser';

DELETE FROM tokens WHERE user_id=(SELECT id FROM users WHERE email="authenticationResourceUser");
DELETE FROM users WHERE email='authenticationResourceUser';

-- We don't need admin's key any more, but we do need the user.
DELETE FROM tokens WHERE user_id=(SELECT id FROM users WHERE email="admin");
