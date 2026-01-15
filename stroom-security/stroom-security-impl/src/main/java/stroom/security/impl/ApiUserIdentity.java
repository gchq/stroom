/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.HasSessionId;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.HasJwtClaims;
import stroom.security.shared.HasUserRef;
import stroom.util.exception.ThrowingFunction;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link UserIdentity} that is created for API requests (that have JWT in the Authorisation header)
 * and UI requests that have come via an AWS ALB (i.e. using the x-amzn-oidc-data header).
 */
class ApiUserIdentity implements UserIdentity, HasSessionId, HasUserRef, HasJwtClaims, HasJwt {

    private final UserRef userRef;
    private final String sessionId;
    private final JwtContext jwtContext;

    /**
     * @param userUuid  The stroom_user UUID
     * @param subjectId The unique ID on the IDP, i.e. the 'sub' claim
     */
    ApiUserIdentity(final String userUuid,
                    final String subjectId,
                    final String displayName,
                    final String sessionId,
                    final JwtContext jwtContext) {
        final Optional<String> fullName = NullSafe.getAsOptional(
                jwtContext.getJwtClaims(),
                ThrowingFunction.unchecked(jwtClaims ->
                        jwtClaims.getClaimValue("name", String.class)));

        this.userRef = UserRef.builder()
                .uuid(userUuid)
                .subjectId(subjectId)
                .displayName(displayName)
                .fullName(fullName.orElse(null))
                .user()
                .enabled()
                .build();
        this.sessionId = sessionId;
        this.jwtContext = jwtContext;
    }

    @Override
    public String subjectId() {
        return userRef.getSubjectId();
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(userRef.getFullName());
    }

    @Override
    public String getDisplayName() {
        return userRef.toDisplayString();
    }

    @Override
    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getJwt() {
        return NullSafe.get(jwtContext, JwtContext::getJwt);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ApiUserIdentity that = (ApiUserIdentity) o;
        return Objects.equals(userRef, that.userRef) &&
               Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userRef, sessionId);
    }

    @Override
    public String toString() {
        return subjectId();
    }

    @Override
    public JwtClaims getJwtClaims() {
        return jwtContext.getJwtClaims();
    }
}
