-- This adds a 'force password change at next login' field.
ALTER TABLE authentication_users
ADD force_password_change BOOLEAN DEFAULT FALSE NOT NULL;
