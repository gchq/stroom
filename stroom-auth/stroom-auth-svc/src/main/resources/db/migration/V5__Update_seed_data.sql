-------------------------------------------------------------------------
-- Update API keys which have expired. These ones won't expired.
-------------------------------------------------------------------------

-- For stroom
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
    'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlzcyI6InN0cm9vbSIsInNpZCI6bnVsbH0.k0Ssb43GCdTunAMeM26fIulYKNUuPUaJJk6GxDmzCPb7kVPwEtdfBSrtwazfEFM97dnmvURkLqs-DAZTXhhf-0VqQx4hkwcCHf83eVptWTy-lufIhQo6FCM223c9ONIhl6CPqknWh9Bo3vFNrNJoKz5Zw2T_iCcQhi2WGjd_tjTG7VbibTIpH3lPQDw1IBD2nMsEqACJSk3IaFe0GYcrAEMwsjj3sjAwByMbj5DJvo_DJbAuzUwS5IVpASEENen5Xd3wALLirrraUfED1OY0G56Ttcwl3uQ2s-grZXBM4JCiIurlWR5iNtNwoPUsZsyMju4FMSXt3Ur1NIpD7XKJlg',
    NOW(),
    (SELECT id FROM users WHERE email = 'stroomServiceUser')
);


-- For stats
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
    'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzdGF0c1NlcnZpY2VVc2VyIiwiaXNzIjoic3Ryb29tIiwic2lkIjpudWxsfQ.u2IEhW3Vswpc8ooeYaVQfPTeOA9kzdbnOyZKHDyam-RxDSS90lgRxUvk1SVqb0-jiz3WWpXF8O4zlIIwbJ01-SElpuC5g1roqn4sKDE3N8iGZxINOlku16eEAjT11pbZvlvSr9RdYJS-s118C92z7Xa6gSN_PwBwWRssUchc6NvKNxtBKpcLPbTe3Fp7cesZGiAm-UzqWpPUBv1giXKGfgTXAnpDSzSPSX2f7h8timgRBaxRfMfos7QvfUDaN4IGkcwDyQPFJt4mYdA7aZrVYLfiNwwwaShlsB7o2TqkS4YqvW7aNa90wjrT43hMNsNoKp2S1Upm_nUYF-QGZIF0fQ',
    NOW(),
    (SELECT id FROM users WHERE email = 'statsServiceUser')
);


