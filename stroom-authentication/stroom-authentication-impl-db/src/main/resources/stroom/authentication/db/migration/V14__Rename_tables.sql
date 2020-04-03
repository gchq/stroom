RENAME TABLE users TO account;
RENAME TABLE tokens TO token;
ALTER TABLE token CHANGE COLUMN token data varchar(1000) NOT NULL;
ALTER TABLE account CHANGE COLUMN force_password_change force_password_change bit(1) NOT NULL;
ALTER TABLE account CHANGE COLUMN never_expires never_expires bit(1) NOT NULL;