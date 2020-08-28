CREATE TABLE oauth_client (
    id 				int(11) NOT NULL AUTO_INCREMENT,
    version         int(11) NOT NULL,
    create_time_ms  bigint(20) NOT NULL,
    create_user     varchar(255) NOT NULL,
    update_time_ms  bigint(20) NOT NULL,
    update_user     varchar(255) NOT NULL,
    name            varchar(255) NOT NULL,
    client_id       varchar(255) NOT NULL,
    client_secret   varchar(255) NOT NULL,
    uri_pattern     longtext,
    PRIMARY KEY     (id),
    UNIQUE 			(client_id),
    UNIQUE 			(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;