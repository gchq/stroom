ALTER TABLE users
ADD never_expires BOOLEAN DEFAULT FALSE NOT NULL;

UPDATE users SET never_expires=TRUE WHERE email='admin';