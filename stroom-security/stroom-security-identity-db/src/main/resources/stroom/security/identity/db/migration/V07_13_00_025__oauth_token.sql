-- ------------------------------------------------------------------------
-- Copyright 2026 Crown Copyright
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- ------------------------------------------------------------------------

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Create the oauth_token table: an inventory of the tokens the INTERNAL IdP has minted, so that they can
-- be listed and revoked before they expire. Externally minted tokens never get a row here - their
-- revocation is the external IdP's business.
--
-- Deliberately NOT called `token`. A legacy `token` table (V07_00_00_025) is dropped by a migration in a
-- DIFFERENT module, stroom-security-impl-db/V07_02_00_115__drop_token_table.sql, which reaches across the
-- schema boundary because cross-module migration order is non-deterministic. That drop is guarded only by
-- an existence check, so it cannot tell a brand new table from the legacy one - reusing the name would
-- mean silent data loss on whichever install orderings ran identity's migrations first.
--
CREATE TABLE IF NOT EXISTS oauth_token (
    id              int NOT NULL AUTO_INCREMENT,
    version         int NOT NULL,
    create_time_ms  bigint NOT NULL,
    create_user     varchar(255) NOT NULL,
    update_time_ms  bigint NOT NULL,
    update_user     varchar(255) NOT NULL,
    -- ACCESS | ID | REFRESH. A plain discriminator, NOT an FK to the legacy token_type table.
    token_type      varchar(10) NOT NULL,
    -- The JWT id, for ACCESS/ID rows only. Refresh tokens are opaque random strings, not JWTs, and have
    -- no jti - which is why this table has a surrogate id PK rather than keying on jti.
    jti             varchar(255) DEFAULT NULL,
    -- SHA-256 of the opaque refresh token, for REFRESH rows only. The redeemable credential is looked up
    -- by presentation and must never be stored in the clear.
    token_hash      varchar(255) DEFAULT NULL,
    -- A subject string, NOT an FK to account.id: service and external subjects have no account row.
    subject_id      varchar(255) NOT NULL,
    client_id       varchar(255) DEFAULT NULL,
    -- Rotation lineage for refresh tokens (grant id for access/id), so reuse can revoke a whole family.
    family_id       varchar(255) DEFAULT NULL,
    -- The scope granted at authentication, carried forward onto every successor token in the family.
    -- Needed to mint the successor when a refresh token is redeemed.
    scope           longtext,
    -- When the end user actually authenticated. Carried forward so a refreshed id token reports the
    -- original login time rather than the time of the refresh.
    auth_time_ms    bigint DEFAULT NULL,
    issued_ms       bigint NOT NULL,
    expires_ms      bigint NOT NULL,
    -- Set when a refresh token is redeemed. The row is KEPT until it expires rather than being deleted,
    -- because a consumed-but-unexpired row is what makes a replay of that token detectable for the whole of
    -- its lifetime.
    consumed_ms     bigint DEFAULT NULL,
    revoked         tinyint NOT NULL DEFAULT '0',
    revoked_ms      bigint DEFAULT NULL,
    revoked_by      varchar(255) DEFAULT NULL,
    PRIMARY KEY (id),
    -- Two nullable natural keys, exactly one of which is populated per token_type.
    UNIQUE KEY oauth_token_jti_idx (jti),
    UNIQUE KEY oauth_token_token_hash_idx (token_hash),
    -- Admin grouping and revoke-by-user.
    KEY oauth_token_subject_id_idx (subject_id),
    -- Family revocation on refresh reuse.
    KEY oauth_token_family_id_idx (family_id),
    -- Drives both the read-time `expires_ms > now` predicate and the purge job.
    KEY oauth_token_expires_ms_idx (expires_ms),
    -- The revoked-and-still-live lookup that builds the verify path's denylist. `revoked` leads because it
    -- is the selective column - almost no rows are revoked - whereas `expires_ms > now` matches nearly
    -- every row and so cannot narrow anything on its own.
    KEY oauth_token_revoked_idx (revoked, expires_ms)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
