package stroom.security.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.HasSessionId;
import stroom.security.api.UserIdentity;
import stroom.security.common.impl.HasJwtClaims;
import stroom.security.shared.HasUserRef;
import stroom.util.NullSafe;
import stroom.util.exception.ThrowingFunction;
import stroom.util.shared.UserRef;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;

class ApiUserIdentity implements UserIdentity, HasSessionId, HasUserRef, HasJwtClaims, HasJwt {

    private final UserRef userRef;
    private final String sessionId;
    private final JwtContext jwtContext;

    /**
     * @param userUuid    The stroom_user UUID
     * @param subjectId   The unique ID on the IDP, i.e. the 'sub' claim
     * @param displayName
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

        this.userRef = new UserRef(userUuid, subjectId, displayName, fullName.orElse(null), false);
        this.sessionId = sessionId;
        this.jwtContext = jwtContext;
    }

    @Override
    public String getSubjectId() {
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
        return getSubjectId();
    }

    @Override
    public JwtClaims getJwtClaims() {
        return jwtContext.getJwtClaims();
    }
}
