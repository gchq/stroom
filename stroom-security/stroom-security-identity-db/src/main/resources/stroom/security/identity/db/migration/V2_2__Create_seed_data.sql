INSERT INTO token_types (token_type) VALUES ("user");
INSERT INTO token_types (token_type) VALUES ("api");
INSERT INTO token_types (token_type) VALUES ("email_reset");

-- This is the public/private key pair first used by Stroom Auth. It's akin to a default password and must be reset.
INSERT INTO json_web_key (
    keyId,
    json)
VALUES (
    '1ec7a983-317d-46ce-ae93-ce42bc217e52',
    '{"kty":"RSA","kid":"1ec7a983-317d-46ce-ae93-ce42bc217e52","n":"72G4eRyG91OlsKOs-s2vBsbvXV5IKDOUpqDkFKYR_pCJwSBgD4BZYZ_qnIXx5L6cIK1Hsk_nMchHnL9lK7-nIt_bO41AyBes2IF2TTPuPKwqGY-aKCcuRh_BkDrCPZBR7-iyuYigL8MwgiP-yX6ieNpXzrfy3C8IYwLrKjLt39fb0fBod6_nrBPZeBUlH5m5pswNDfbOJII3bEGq5uQw5SZbaGBEEVbvkLNTROByK1PuOezfrpAyTWHCsG-zEsQ0HuIFyVo07sFJhV1AypPL-s71vKi6sDY46Mc5I2KV4CHbdvIhHT4xTaAhE9VimZgQW6sVvpRalV5XL8czjWVjtw","e":"AQAB","d":"HnwZXAMQBQs3_Ii7jK0I7xoCfad2FPiMo7O1mBOWEw8hG-EdmpvDxjTxUcGVDoZfp6GpkcGvNZ3F0OZm4e1kQYK0jp7scw7gyimigS5t1nguXFb3UMm8kN2WbuGsvt5UMPM3X31QuQRodwpSdiKUWkOkDwVJ_lRXAxTqEdOui2TY2WzKijvBEahyA2pqUGvrajF2GKFbZzXO1zagq6VhGCQohvCkaRWB-tVzc4-Ns6v3jqCKRpYYPrxD4C0RZ1K6InkxtXg1qYrcGMAPzCbqdjLoN41k-ZLNrp8rUSbRf2JAv9A8Ca2r6GDa0PcsySIbUWfduXOgGSuSfW6Hze1nMQ","p":"__6DyUpzhXxh6JfRghv2K371tCeV4l1Q45S92Ea1IX3Y6iBweK4W6ToQYTM7l_0bA5eaJ3kywvmpFqzpEcmtsOJOyg_rPQe3GeBDhUJLHqEPDT4t2yc8eQnrFOdWLEYg2nYWLoOkFPS36UBgJtaxIpyj16qidk-DYSEXpeDcqkM","q":"72McA2LIVj3hjlt_JsLpMFpcMm7E6knS-YITQUlwIFmVcW5tNQkC871o5x5AJL2Zj7MwxGHEx1WLzwd8B6lGkFP4BTpjCEoo_kXS-6IWyI3p6v7EmIb1O5Av8W37CA3VkPtuM-_dTFGlEW2vF0_ZetvTL49FlRx01Ekt48pvK30","dp":"xEAYQ_6hpVn_rVKGORq6lAnWz2_xhgJH-tCS4fUC81QJMSQBVWMRCWeMGxgtvY06Ynycn1pYwgSnzkxsuUhFse8su9eMXdNGWb4FxWlXMXoDkgFzIiloQNqLsBDRjUuN8CzLQImHBtG9FEJX9C5uybwQF0wnFFBMxe-as345bQU","dq":"1z03vNue4dw16Dfgdcueu7kjWL08FKRYK7uG8JbFWHDz68-sJZl6rAlMPzJ13hMT9Z7aZFi8A7apRHaoUIMlTTQStzCuRo_Xl_jUISi2b5EaGA8GWVZPPUUBtoR6x90Yf4lypwQu6CYo0yjZ244SL2Nj2Ulq-Q1jBlTeDAjCOEk","qi":"a6D1TYa8dgJDqKfZ926SqPxzA3FiJPWAfo8BTyIahZFqqaPAaXEfKUTTCZKmIqsWwNntGcdmDhnNGZ11E2X8MPYkRpnIQj3BywtDzRbPGJ8AFcvy6Zq_vYl874LFrkvGPILe6NxabRCyrFgDKOQ-gIjkL83t0wyS_vVlrGLtCrk"}'
);

-- Create seed accounts
-- Must be reset In the running instance after a production deployment.
INSERT INTO users (
    email,
    password_hash,
    comments,
    created_on,
    created_by_user)
VALUES (
    "admin",
    "$2a$10$THzPVeDX70fBaFPjZoY1fOXnCCAezhhYV/LO09w.3JKIybPgRMSiW", -- admin
    "Built-in admin user",
    CURRENT_TIMESTAMP,
    "Flyway migration"
);

-- Here we create service user accounts and API keys.
-- All API keys need to be associated with a user account.
-- These API keys are signed with the RSA private key in json_web_key.
-- Must be reset in the running instance after a production deployment.
-- For stroom
INSERT INTO users (
    email,
    password_hash,
    state,
    comments,
    created_on,
    created_by_user)
VALUES (
    "stroomServiceUser",
    "Fake hash: this is a service user and doesn't have a password",
    "locked",
    "This is a service account and used for associating with an API token not logging in: it's locked and must remain so.",
    CURRENT_TIMESTAMP,
    "Flyway migration"
);
-- NB: If you update this insert you must remember to update Base_IT too, otherwise the integration tests might fail!
INSERT INTO tokens(
    user_id,
    token_type_id,
    token,
    issued_on,
    issued_by_user
)
VALUES (
    (SELECT id FROM users WHERE email = 'stroomServiceUser'),
    (SELECT id FROM token_types WHERE token_type = 'api'),
    -- This token is for admin and doesn't have an expiration.
    'eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjE1NDQ4NjMwNDMsInN1YiI6InN0cm9vbVNlcnZpY2VVc2VyIiwiaXNzIjoic3Ryb29tIn0.gHJkpxeW5CjU_hBzuLhQd8Ot8XkLvhu45_-Ql4gOX96iNbl0AnKEwKu2QmMY3uVerGjYBHDczgKJlLVF_RQtFiwFLorT2P_Mv-9ShcCL0Ml-Tq-1i-_UnHMYHP5Nv-rP3ajUz-vTHwIYqi_WU-IEpIF56MCYBqeDkgQfe-I03VyfsLkWt-3f8L3AKESZirmqjPUB_SPi4vWGpyN28FuJe1KyqdCPo5QVKnrM_dpguE_aIj1Dy1sovmgO5WxFm3-hE7asW3WrnokSopNXQ1bJ3W77v4k1CnMpYDw5schQAKqUffPVGxNE6UxNunZTlRQJQqYihKkhpeiTiZMo9XCHGg',
    NOW(),
    (SELECT id FROM users WHERE email = 'stroomServiceUser')
);


-- For stats
INSERT INTO users (
    email,
    password_hash,
    state,
    comments,
    created_on,
    created_by_user)
VALUES (
    "statsServiceUser",
    "Fake hash: this is a service user and doesn't have a password",
    "locked",
    "This is a service account and used for associating with an API token not logging in: it's locked and must remain so.",
    CURRENT_TIMESTAMP,
    "Flyway migration"
);
INSERT INTO tokens(
    user_id,
    token_type_id,
    token,
    issued_on,
    issued_by_user
)
VALUES (
    (SELECT id FROM users WHERE email = 'statsServiceUser'),
    (SELECT id FROM token_types WHERE token_type = 'api'),
    -- This token is for admin and doesn't have an expiration.
    'eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjE1NDQ4NjMwNjIsInN1YiI6InN0YXRzU2VydmljZVVzZXIiLCJpc3MiOiJzdHJvb20ifQ.7AFPkNgM1UBL_Pj-K5kSNPClgDJ6wZ22WWukjWGw_myZWuMZPGd-kqYxUuQzqqmTA918wylFSx5xBPWA1oCbx0aEPGEOdMnUViykq5XaGEHwPGT9Tf9JI0h8z6-TfOt2VJ2CFsSmRSpfe1CYOywZwziqBwvf5m0rWhfb0wm1abcBnjmX_EfxZiV3McmY0MEzSN6AkYGyWr4ggja06onKEObkoZ9f5pRt7tkTsBpCvaolavfu3IF5FXP9GRifOcxdQXFOgRCDe4JkG6ZAeKbTT6aJJCU798F9jL2ozIw-tQTrA5KjkwIpefaA6CoA_mZd-gIa-ZOLVGzRaIMBo-dl-g',
    NOW(),
    (SELECT id FROM users WHERE email = 'statsServiceUser')
);



-- For the AuthenticationResource
INSERT INTO users (
    email,
    password_hash,
    state,
    comments,
    created_on,
    created_by_user)
VALUES (
    "authenticationResourceUser",
    "Fake hash: this is a service user and doesn't have a password",
    "locked",
    "This is a service account and used for associating with an API token not logging in: it's locked and must remain so.",
    CURRENT_TIMESTAMP,
    "Flyway migration"
);