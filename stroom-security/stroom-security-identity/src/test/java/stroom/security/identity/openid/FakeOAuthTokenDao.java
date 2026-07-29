/*
 * Copyright 2016-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.openid;

import stroom.security.identity.token.OAuthToken;
import stroom.security.identity.token.OAuthTokenDao;
import stroom.security.identity.token.OAuthTokenType;
import stroom.security.identity.token.RefreshRotationResult;
import stroom.security.identity.token.RefreshRotationResult.Outcome;
import stroom.security.openid.api.TokenSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Records what was persisted and returns a canned rotation outcome. Hand written rather than mocked so
 * assertions read as "this is what was written" rather than as verifications.
 * <p>
 * The real transactional behaviour is covered against a database in {@code TestOAuthTokenDaoImpl}; this exists
 * to let the identity module test its own callers without one.
 * </p>
 */
class FakeOAuthTokenDao implements OAuthTokenDao {

    final List<OAuthToken> created = new ArrayList<>();
    RefreshRotationResult result = RefreshRotationResult.of(Outcome.UNKNOWN);
    String presentedHash;
    String successorHash;
    Long successorExpiresMs;

    List<OAuthToken> createdOfType(final OAuthTokenType tokenType) {
        return created.stream()
                .filter(token -> token.tokenType() == tokenType)
                .toList();
    }

    @Override
    public OAuthToken create(final OAuthToken oAuthToken) {
        created.add(oAuthToken);
        return oAuthToken;
    }

    @Override
    public RefreshRotationResult rotateRefreshToken(final String presentedTokenHash,
                                                    final String successorTokenHash,
                                                    final long successorExpiresMs,
                                                    final long nowMs,
                                                    final String replayRevokedBy) {
        this.presentedHash = presentedTokenHash;
        this.successorHash = successorTokenHash;
        this.successorExpiresMs = successorExpiresMs;
        return result;
    }

    @Override
    public Optional<OAuthToken> fetchByJti(final String jti, final long nowMs) {
        return Optional.empty();
    }

    @Override
    public Optional<OAuthToken> fetchByTokenHash(final String tokenHash, final long nowMs) {
        return Optional.empty();
    }

    @Override
    public List<OAuthToken> findBySubjectId(final String subjectId, final long nowMs) {
        return List.of();
    }

    @Override
    public Set<String> fetchRevokedJtis(final long nowMs) {
        return Set.of();
    }

    @Override
    public Map<String, TokenSummary> summariseUsableBySubject(final long nowMs) {
        return new HashMap<>();
    }

    @Override
    public int revokeBySubjectId(final String subjectId, final String revokedBy, final long nowMs) {
        return 0;
    }

    @Override
    public int revokeByFamilyId(final String familyId, final String revokedBy, final long nowMs) {
        return 0;
    }

    @Override
    public int revokeGrantByJti(final String jti, final String revokedBy, final long nowMs) {
        return 0;
    }

    @Override
    public boolean revokeByJti(final String jti, final String revokedBy, final long nowMs) {
        return false;
    }

    @Override
    public int deleteExpired(final long nowMs) {
        return 0;
    }
}
