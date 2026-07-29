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

package stroom.security.identity.dao;

import stroom.db.util.JooqUtil;
import stroom.security.identity.db.IdentityDbConnProvider;
import stroom.security.identity.db.jooq.tables.records.OauthTokenRecord;
import stroom.security.identity.token.OAuthToken;
import stroom.security.identity.token.OAuthTokenDao;
import stroom.security.identity.token.OAuthTokenType;
import stroom.security.identity.token.RefreshRotationResult;
import stroom.security.identity.token.RefreshRotationResult.Outcome;
import stroom.security.openid.api.TokenSummary;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static stroom.security.identity.db.jooq.tables.OauthToken.OAUTH_TOKEN;

/**
 * @see OAuthTokenDao
 */
@Singleton
class OAuthTokenDaoImpl implements OAuthTokenDao {

    private static final String SYSTEM_USER = "system";

    private final IdentityDbConnProvider identityDbConnProvider;

    @Inject
    OAuthTokenDaoImpl(final IdentityDbConnProvider identityDbConnProvider) {
        this.identityDbConnProvider = identityDbConnProvider;
    }

    @Override
    public OAuthToken create(final OAuthToken oAuthToken) {
        final long now = System.currentTimeMillis();
        final OauthTokenRecord record = JooqUtil.contextResult(identityDbConnProvider, context -> {
            final OauthTokenRecord newRecord = context.newRecord(OAUTH_TOKEN);
            newRecord.setVersion(1);
            newRecord.setCreateTimeMs(now);
            newRecord.setCreateUser(SYSTEM_USER);
            newRecord.setUpdateTimeMs(now);
            newRecord.setUpdateUser(SYSTEM_USER);
            newRecord.setTokenType(oAuthToken.tokenType().name());
            newRecord.setJti(oAuthToken.jti());
            newRecord.setTokenHash(oAuthToken.tokenHash());
            newRecord.setSubjectId(oAuthToken.subjectId());
            newRecord.setClientId(oAuthToken.clientId());
            newRecord.setFamilyId(oAuthToken.familyId());
            newRecord.setScope(oAuthToken.scope());
            newRecord.setAuthTimeMs(oAuthToken.authTimeMs());
            newRecord.setIssuedMs(oAuthToken.issuedMs());
            newRecord.setExpiresMs(oAuthToken.expiresMs());
            newRecord.setConsumedMs(oAuthToken.consumedMs());
            newRecord.setRevoked(oAuthToken.revoked());
            newRecord.setRevokedMs(oAuthToken.revokedMs());
            newRecord.setRevokedBy(oAuthToken.revokedBy());
            newRecord.store();
            return newRecord;
        });
        return mapRecord(record);
    }

    @Override
    public RefreshRotationResult rotateRefreshToken(final String presentedTokenHash,
                                                    final String successorTokenHash,
                                                    final long successorExpiresMs,
                                                    final long nowMs,
                                                    final String replayRevokedBy) {
        if (presentedTokenHash == null) {
            return RefreshRotationResult.of(Outcome.UNKNOWN);
        }
        return JooqUtil.transactionResult(identityDbConnProvider, context -> {
            // Lock the row for the duration of the transaction so a concurrent redemption of the same token
            // serialises behind us rather than reading the same pre-consumption state.
            final OauthTokenRecord record = context
                    .selectFrom(OAUTH_TOKEN)
                    .where(OAUTH_TOKEN.TOKEN_HASH.eq(presentedTokenHash))
                    .forUpdate()
                    .fetchOne();

            if (record == null) {
                // Never issued, or expired and since purged. Deliberately not distinguished.
                return RefreshRotationResult.of(Outcome.UNKNOWN);
            }
            if (Boolean.TRUE.equals(record.getRevoked())) {
                return RefreshRotationResult.of(Outcome.REVOKED);
            }
            if (record.getExpiresMs() <= nowMs) {
                return RefreshRotationResult.of(Outcome.EXPIRED);
            }
            if (record.getConsumedMs() != null) {
                // Already redeemed, so this is a replay of a spent token. The safe response is to assume the
                // token was stolen and kill every live token descended from the same login, not just this
                // one - the thief may already hold a valid successor.
                final int revoked = context
                        .update(OAUTH_TOKEN)
                        .set(OAUTH_TOKEN.REVOKED, true)
                        .set(OAUTH_TOKEN.REVOKED_MS, nowMs)
                        .set(OAUTH_TOKEN.REVOKED_BY, replayRevokedBy)
                        .set(OAUTH_TOKEN.UPDATE_TIME_MS, nowMs)
                        .set(OAUTH_TOKEN.UPDATE_USER, replayRevokedBy != null
                                ? replayRevokedBy
                                : SYSTEM_USER)
                        .set(OAUTH_TOKEN.VERSION, OAUTH_TOKEN.VERSION.plus(1))
                        .where(OAUTH_TOKEN.FAMILY_ID.eq(record.getFamilyId()))
                        .and(OAUTH_TOKEN.REVOKED.isFalse())
                        .and(OAUTH_TOKEN.EXPIRES_MS.gt(nowMs))
                        .execute();
                return RefreshRotationResult.replayed(revoked);
            }

            // Compare-and-set: only the caller that flips consumed_ms from null wins. Belt and braces given
            // the row lock above, but it is the guarantee that does not depend on the lock being held.
            final int consumed = context
                    .update(OAUTH_TOKEN)
                    .set(OAUTH_TOKEN.CONSUMED_MS, nowMs)
                    .set(OAUTH_TOKEN.UPDATE_TIME_MS, nowMs)
                    .set(OAUTH_TOKEN.UPDATE_USER, SYSTEM_USER)
                    .set(OAUTH_TOKEN.VERSION, OAUTH_TOKEN.VERSION.plus(1))
                    .where(OAUTH_TOKEN.ID.eq(record.getId()))
                    .and(OAUTH_TOKEN.CONSUMED_MS.isNull())
                    .execute();
            if (consumed == 0) {
                return RefreshRotationResult.of(Outcome.LOST_RACE);
            }

            // Same transaction as the consume, so there is no window in which the presented token is spent
            // but its successor does not exist.
            final OauthTokenRecord successor = context.newRecord(OAUTH_TOKEN);
            successor.setVersion(1);
            successor.setCreateTimeMs(nowMs);
            successor.setCreateUser(SYSTEM_USER);
            successor.setUpdateTimeMs(nowMs);
            successor.setUpdateUser(SYSTEM_USER);
            successor.setTokenType(OAuthTokenType.REFRESH.name());
            successor.setTokenHash(successorTokenHash);
            successor.setSubjectId(record.getSubjectId());
            successor.setClientId(record.getClientId());
            // Same family, so a later replay anywhere in the lineage revokes this one too.
            successor.setFamilyId(record.getFamilyId());
            successor.setScope(record.getScope());
            successor.setAuthTimeMs(record.getAuthTimeMs());
            successor.setIssuedMs(nowMs);
            successor.setExpiresMs(successorExpiresMs);
            successor.setRevoked(false);
            successor.store();

            return RefreshRotationResult.rotated(mapRecord(record));
        });
    }

    @Override
    public Optional<OAuthToken> fetchByJti(final String jti, final long nowMs) {
        if (jti == null) {
            return Optional.empty();
        }
        return fetchOne(OAUTH_TOKEN.JTI.eq(jti), nowMs);
    }

    @Override
    public Optional<OAuthToken> fetchByTokenHash(final String tokenHash, final long nowMs) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        return fetchOne(OAUTH_TOKEN.TOKEN_HASH.eq(tokenHash), nowMs);
    }

    private Optional<OAuthToken> fetchOne(final Condition condition, final long nowMs) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(OAUTH_TOKEN)
                        .where(condition)
                        .and(notExpired(nowMs))
                        .fetchOptional())
                .map(OAuthTokenDaoImpl::mapRecord);
    }

    @Override
    public List<OAuthToken> findBySubjectId(final String subjectId, final long nowMs) {
        if (subjectId == null) {
            return List.of();
        }
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(OAUTH_TOKEN)
                        .where(OAUTH_TOKEN.SUBJECT_ID.eq(subjectId))
                        .and(notExpired(nowMs))
                        .orderBy(OAUTH_TOKEN.ISSUED_MS.desc(), OAUTH_TOKEN.ID.desc())
                        .fetch())
                .stream()
                .map(OAuthTokenDaoImpl::mapRecord)
                .toList();
    }

    @Override
    public Set<String> fetchRevokedJtis(final long nowMs) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .select(OAUTH_TOKEN.JTI)
                        .from(OAUTH_TOKEN)
                        .where(OAUTH_TOKEN.REVOKED.isTrue())
                        .and(OAUTH_TOKEN.JTI.isNotNull())
                        .and(notExpired(nowMs))
                        .fetch(OAUTH_TOKEN.JTI))
                .stream()
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, TokenSummary> summariseUsableBySubject(final long nowMs) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .select(OAUTH_TOKEN.SUBJECT_ID,
                                DSL.count(),
                                DSL.min(OAUTH_TOKEN.EXPIRES_MS),
                                DSL.max(OAUTH_TOKEN.EXPIRES_MS))
                        .from(OAUTH_TOKEN)
                        .where(OAUTH_TOKEN.REVOKED.isFalse())
                        // A spent refresh token is not live access, even though its row is retained for
                        // replay detection. Always null for JWTs, so this is a no-op for them.
                        .and(OAUTH_TOKEN.CONSUMED_MS.isNull())
                        .and(notExpired(nowMs))
                        .groupBy(OAUTH_TOKEN.SUBJECT_ID)
                        .fetch())
                .stream()
                .collect(Collectors.toMap(
                        record -> record.get(OAUTH_TOKEN.SUBJECT_ID),
                        record -> new TokenSummary(
                                record.get(DSL.count()),
                                record.get(DSL.min(OAUTH_TOKEN.EXPIRES_MS)),
                                record.get(DSL.max(OAUTH_TOKEN.EXPIRES_MS)))));
    }

    @Override
    public int revokeBySubjectId(final String subjectId, final String revokedBy, final long nowMs) {
        if (subjectId == null) {
            return 0;
        }
        return revokeWhere(OAUTH_TOKEN.SUBJECT_ID.eq(subjectId), revokedBy, nowMs);
    }

    @Override
    public int revokeByFamilyId(final String familyId, final String revokedBy, final long nowMs) {
        if (familyId == null) {
            return 0;
        }
        return revokeWhere(OAUTH_TOKEN.FAMILY_ID.eq(familyId), revokedBy, nowMs);
    }

    @Override
    public int revokeGrantByJti(final String jti, final String revokedBy, final long nowMs) {
        if (jti == null) {
            return 0;
        }
        return JooqUtil.transactionResult(identityDbConnProvider, context -> {
            // Deliberately no expiry filter on the lookup: the access token naming the grant is the first thing
            // to expire, while the refresh token that actually needs revoking outlives it by weeks.
            final String familyId = context
                    .select(OAUTH_TOKEN.FAMILY_ID)
                    .from(OAUTH_TOKEN)
                    .where(OAUTH_TOKEN.JTI.eq(jti))
                    .fetchOne(OAUTH_TOKEN.FAMILY_ID);
            if (familyId == null) {
                return 0;
            }
            return revokeWhere(context, OAUTH_TOKEN.FAMILY_ID.eq(familyId), revokedBy, nowMs);
        });
    }

    @Override
    public boolean revokeByJti(final String jti, final String revokedBy, final long nowMs) {
        if (jti == null) {
            return false;
        }
        return revokeWhere(OAUTH_TOKEN.JTI.eq(jti), revokedBy, nowMs) > 0;
    }

    /**
     * Revoke the usable tokens matching a condition.
     * <p>
     * Three classes of row are deliberately left alone, so that the returned count is the number of tokens
     * actually taken away rather than the number of rows touched:
     * </p>
     * <ul>
     *     <li><b>Already revoked</b> - re-stamping would destroy the original {@code revoked_by} /
     *     {@code revoked_ms} provenance, and the token is already gone.</li>
     *     <li><b>Expired</b> - already unusable, so revoking is a no-op with a misleading count.</li>
     *     <li><b>Consumed refresh tokens</b> - a spent token cannot be redeemed again anyway, and marking one
     *     revoked would actively lose information: presenting a spent token is the signature of a stolen one,
     *     and {@link #rotateRefreshToken} tests {@code revoked} before {@code consumed_ms}, so a revoked-and-
     *     spent row would be reported as merely revoked instead of as a replay - suppressing both the warning
     *     and the family revocation that a replay triggers.</li>
     * </ul>
     */
    private int revokeWhere(final Condition condition, final String revokedBy, final long nowMs) {
        return JooqUtil.contextResult(identityDbConnProvider, context ->
                revokeWhere(context, condition, revokedBy, nowMs));
    }

    private int revokeWhere(final DSLContext context,
                            final Condition condition,
                            final String revokedBy,
                            final long nowMs) {
        return context
                .update(OAUTH_TOKEN)
                .set(OAUTH_TOKEN.REVOKED, true)
                .set(OAUTH_TOKEN.REVOKED_MS, nowMs)
                .set(OAUTH_TOKEN.REVOKED_BY, revokedBy)
                .set(OAUTH_TOKEN.UPDATE_TIME_MS, nowMs)
                .set(OAUTH_TOKEN.UPDATE_USER, revokedBy != null
                        ? revokedBy
                        : SYSTEM_USER)
                .set(OAUTH_TOKEN.VERSION, OAUTH_TOKEN.VERSION.plus(1))
                .where(condition)
                .and(OAUTH_TOKEN.REVOKED.isFalse())
                .and(OAUTH_TOKEN.CONSUMED_MS.isNull())
                .and(notExpired(nowMs))
                .execute();
    }

    @Override
    public int deleteExpired(final long nowMs) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                .deleteFrom(OAUTH_TOKEN)
                .where(OAUTH_TOKEN.EXPIRES_MS.le(nowMs))
                .execute());
    }

    /**
     * The read-time expiry predicate. Deliberately applied to every read so that an unswept expired row is
     * never honoured, making correctness independent of the purge job.
     */
    private static Condition notExpired(final long nowMs) {
        return OAUTH_TOKEN.EXPIRES_MS.gt(nowMs);
    }

    private static OAuthToken mapRecord(final OauthTokenRecord record) {
        return new OAuthToken(
                record.getId(),
                OAuthTokenType.valueOf(record.getTokenType()),
                record.getJti(),
                record.getTokenHash(),
                record.getSubjectId(),
                record.getClientId(),
                record.getFamilyId(),
                record.getScope(),
                record.getAuthTimeMs(),
                record.getIssuedMs(),
                record.getExpiresMs(),
                record.getConsumedMs(),
                Boolean.TRUE.equals(record.getRevoked()),
                record.getRevokedMs(),
                record.getRevokedBy());
    }
}
