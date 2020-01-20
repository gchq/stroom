ALTER TABLE authentication_users
ADD never_expires BOOLEAN DEFAULT FALSE NOT NULL;

UPDATE authentication_users SET never_expires=TRUE WHERE email='admin';