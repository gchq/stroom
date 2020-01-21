
-- Following Simon Holywell's style guide: http://www.sqlstyle.guide/


-- USERS
CREATE TABLE users (
    id 				      MEDIUMINT NOT NULL AUTO_INCREMENT,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    password_last_changed TIMESTAMP NULL,
    state                 VARCHAR(10) DEFAULT 'enabled', -- enabled, disabled, locked
    first_name            VARCHAR(255),
    last_name             VARCHAR(255),
    comments              TEXT NULL,
    login_failures        INT DEFAULT 0,
    login_count           INT DEFAULT 0,
    last_login            TIMESTAMP NULL,
    created_on 			  TIMESTAMP NULL,
    created_by_user		  VARCHAR(255) NULL,
    updated_on 			  TIMESTAMP NULL,
    updated_by_user 	  VARCHAR(255) NULL,
    PRIMARY KEY           (id),
    UNIQUE 			      (email)
) ENGINE=InnoDB DEFAULT CHARSET latin1;


-- TOKENS / API KEYS
CREATE TABLE token_types (
    id 				      MEDIUMINT NOT NULL AUTO_INCREMENT,
    token_type             VARCHAR(255) NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE 			      (id)
) ENGINE=InnoDB DEFAULT CHARSET latin1;

CREATE TABLE tokens (
    id 				      MEDIUMINT NOT NULL AUTO_INCREMENT,
    user_id               MEDIUMINT NOT NULL, -- The token belongs to this user
    token_type_id         MEDIUMINT NOT NULL,
    token                 VARCHAR(1000) NOT NULL,
    expires_on            TIMESTAMP NULL,
    comments              VARCHAR(500) NULL,
    issued_on             TIMESTAMP NOT NULL,
    issued_by_user		  MEDIUMINT  NULL,
    enabled               BIT DEFAULT 1,
    updated_on 			  TIMESTAMP NULL,
    updated_by_user 	  MEDIUMINT NULL,
    PRIMARY KEY           (id),
    UNIQUE 			      (id),
    CONSTRAINT            `fk_issued_to`
        FOREIGN KEY(user_id) REFERENCES users(id)
        ON DELETE CASCADE -- We want tokens to be removed when users are
        ON UPDATE RESTRICT, -- We don't want the user's ID changing if we have a token
    CONSTRAINT            `fk_issued_by_user`
        FOREIGN KEY(issued_by_user) REFERENCES users(id)
        ON DELETE CASCADE -- We want tokens to be removed when users are
        ON UPDATE RESTRICT, -- We don't want the user's ID changing if we have a token
    CONSTRAINT            `fk_updated_by_user`
        FOREIGN KEY(updated_by_user) REFERENCES users(id)
        ON DELETE CASCADE -- We want tokens to be removed when users are
        ON UPDATE RESTRICT, -- We don't want the user's ID changing if we have a token
    CONSTRAINT            `fk_token_type_id`
        FOREIGN KEY(token_type_id) REFERENCES token_types(id)
        ON DELETE CASCADE -- If we ever delete a token type we will want to delete these too
        ON UPDATE RESTRICT -- We don't want the token type's ID changing if we have a token
) ENGINE=InnoDB DEFAULT CHARSET latin1;


-- JWKs
CREATE TABLE json_web_key (
    id 				      MEDIUMINT NOT NULL AUTO_INCREMENT,
    keyId                 VARCHAR(255) NOT NULL,
    json                  VARCHAR(2000) NOT NULL,
    PRIMARY KEY           (id),
    UNIQUE                (keyId),
    CHECK                 (JSON_VALID(json))
) ENGINE=InnoDB DEFAULT CHARSET latin1;
