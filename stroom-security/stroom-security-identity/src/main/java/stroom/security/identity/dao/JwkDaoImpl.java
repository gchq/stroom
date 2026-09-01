/*
 * Copyright 2020 Crown Copyright
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
import stroom.security.identity.account.InternalIdpProcessingUserIdentity;
import stroom.security.identity.db.IdentityDbConnProvider;
import stroom.security.identity.db.jooq.tables.records.JsonWebKeyRecord;
import stroom.security.identity.shared.SigningKeyRow;
import stroom.security.identity.shared.SigningKeyStatus;
import stroom.security.identity.token.JwkDao;
import stroom.security.identity.token.JwkRotationSummary;
import stroom.security.openid.api.JsonWebKeyFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jose4j.jwk.PublicJsonWebKey;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static stroom.security.identity.db.jooq.tables.JsonWebKey.JSON_WEB_KEY;

/**
 * The signing keys of the internal identity provider.
 * <p>
 * This is deliberately lockless. Two nodes creating a key at the same moment is harmless: both keys
 * are published, both verify (resolution is by {@code kid}), and signing picks the newest
 * deterministically. So rather than serialise creation behind a lock, we tolerate the rare duplicate
 * and let {@link #rotate} tidy any surplus. The one thing that must hold, that a key is never deleted
 * while a token it signed could still verify, is enforced by retention, not by locking.
 * </p>
 */
@Singleton
public class JwkDaoImpl implements JwkDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwkDaoImpl.class);

    /**
     * Stamped on rows written by the system rather than by a person: creating a key, and retiring one on
     * schedule. Nobody signs in as this, so it cannot be confused with an act by a human - which
     * {@code "admin"}, previously used here, could be, there being a seeded account of that name.
     * <p>
     * This matters most for {@code update_user}, where a scheduled retirement now shares a column with an
     * administrator's deliberate revocation. Those are the two events an incident most needs to tell apart.
     * </p>
     */
    private static final String SYSTEM_AUDIT_USER = InternalIdpProcessingUserIdentity.INTERNAL_PROCESSING_USER;

    private final IdentityDbConnProvider identityDbConnProvider;
    private final JsonWebKeyFactory jsonWebKeyFactory;
    private final KeyTypeDaoImpl keyTypeDao;

    @Inject
    public JwkDaoImpl(final IdentityDbConnProvider identityDbConnProvider,
                      final KeyTypeDaoImpl keyTypeDao,
                      final JsonWebKeyFactory jsonWebKeyFactory) {
        this.identityDbConnProvider = identityDbConnProvider;
        this.keyTypeDao = keyTypeDao;
        this.jsonWebKeyFactory = jsonWebKeyFactory;
    }

    @Override
    public List<PublicJsonWebKey> listPublishable() {
        final long now = System.currentTimeMillis();
        List<JsonWebKeyRecord> records = JooqUtil.contextResult(identityDbConnProvider, context ->
                fetchPublishable(context, now));

        // If nothing is publishable there is also no active key, so make one. This is the bootstrap
        // case, and the case where every key has been retired or revoked.
        if (records.isEmpty()) {
            final JsonWebKeyRecord created = JooqUtil.contextResult(identityDbConnProvider, context ->
                    insertActiveKey(context, now));
            records = JooqUtil.contextResult(identityDbConnProvider, context ->
                    fetchPublishable(context, System.currentTimeMillis()));
            if (records.isEmpty()) {
                // The key we just made was retired or revoked again before this re-read; fall back to
                // it so the published set is never empty.
                records = List.of(created);
            }
        }

        return records
                .stream()
                .map(this::toPublicJsonWebKey)
                .toList();
    }

    @Override
    public PublicJsonWebKey getActiveKey() {
        JsonWebKeyRecord active = JooqUtil.contextResult(identityDbConnProvider, this::fetchActive);
        if (active == null) {
            // No key to sign with. Rare: first ever use, or every key retired/revoked. Create one and
            // return it directly; if another node does the same at the same moment the surplus is
            // harmless and rotate() reconciles it.
            active = JooqUtil.contextResult(identityDbConnProvider, context ->
                    insertActiveKey(context, System.currentTimeMillis()));
        }
        return toPublicJsonWebKey(active);
    }

    @Override
    public JwkRotationSummary rotate(final Duration rotationInterval, final Duration retention) {
        final long now = System.currentTimeMillis();

        return JooqUtil.transactionResult(identityDbConnProvider, context -> {
            final List<JsonWebKeyRecord> actives = fetchActiveKeys(context);

            if (actives.isEmpty()) {
                // Nothing to sign with, so create one. Nothing to retire.
                final String created = insertActiveKey(context, now).getKeyId();
                return new JwkRotationSummary(created, null, 0, deleteExpired(context, now));
            }

            // There should be exactly one active key, but a lockless bootstrap race can leave more.
            // Keep the newest and retire the rest, so the published set stays minimal.
            final JsonWebKeyRecord newest = actives.getFirst();
            int reconciled = 0;
            for (final JsonWebKeyRecord surplus : actives.subList(1, actives.size())) {
                reconciled += casRetire(context, surplus, now, now + retention.toMillis());
            }
            if (reconciled > 0) {
                final int finalReconciled = reconciled;
                LOGGER.info(() -> LogUtil.message(
                        "Retired {} surplus active identity signing key(s)", finalReconciled));
            }

            String created = null;
            String retired = null;
            if (now - newest.getCreateTimeMs() >= rotationInterval.toMillis()) {
                // Retire first, and only the node that wins the retirement inserts the replacement.
                // The condition on expires_on_ms is the compare-and-swap: a second node retrying the
                // same key matches no rows and stands down, so no lock is needed.
                if (casRetire(context, newest, now, now + retention.toMillis()) == 1) {
                    created = insertActiveKey(context, now).getKeyId();
                    retired = newest.getKeyId();
                }
            }

            // An expired key is one whose retention has elapsed since it was retired, so every token
            // it signed has expired. An active key has a null expiry and so can never match here.
            final int deleted = deleteExpired(context, now);

            return new JwkRotationSummary(created, retired, reconciled, deleted);
        });
    }

    /**
     * The four columns are named rather than taking the whole row. {@code selectFrom} would fetch the
     * {@code json} column too, putting private key material in memory on every refresh of a screen that
     * exists precisely to avoid handling it - where one incautious log of the record would expose it.
     * Naming the columns means it is never read.
     */
    @Override
    public List<SigningKeyRow> list() {
        final long now = System.currentTimeMillis();
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .select(JSON_WEB_KEY.ID,
                                JSON_WEB_KEY.ENABLED,
                                JSON_WEB_KEY.CREATE_TIME_MS,
                                JSON_WEB_KEY.EXPIRES_ON_MS)
                        .from(JSON_WEB_KEY)
                        .orderBy(JSON_WEB_KEY.CREATE_TIME_MS.desc(), JSON_WEB_KEY.ID.desc())
                        .fetch())
                .map(record -> toSigningKeyRow(
                        record.get(JSON_WEB_KEY.ID),
                        record.get(JSON_WEB_KEY.ENABLED),
                        record.get(JSON_WEB_KEY.CREATE_TIME_MS),
                        record.get(JSON_WEB_KEY.EXPIRES_ON_MS),
                        now));
    }

    /**
     * Only what a key is doing and when, never what it is.
     */
    private static SigningKeyRow toSigningKeyRow(final int id,
                                                 final boolean enabled,
                                                 final long createTimeMs,
                                                 final Long expiresOnMs,
                                                 final long now) {
        final SigningKeyStatus status;
        if (!enabled) {
            status = SigningKeyStatus.REVOKED;
        } else if (expiresOnMs == null) {
            status = SigningKeyStatus.ACTIVE;
        } else if (expiresOnMs > now) {
            status = SigningKeyStatus.RETIRED;
        } else {
            status = SigningKeyStatus.EXPIRED;
        }

        // A revoked key is not trusted whatever its expiry says, and an active key has no expiry stamped,
        // so neither has a date to give.
        final Long effectiveExpiry = status == SigningKeyStatus.REVOKED || status == SigningKeyStatus.ACTIVE
                ? null
                : expiresOnMs;

        return new SigningKeyRow(id, status, createTimeMs, effectiveExpiry);
    }

    @Override
    public int revoke(final int id, final String auditUser) {
        final long now = System.currentTimeMillis();
        return JooqUtil.transactionResult(identityDbConnProvider, context -> {
            final int revoked = context
                    .update(JSON_WEB_KEY)
                    .set(JSON_WEB_KEY.VERSION, JSON_WEB_KEY.VERSION.plus(1))
                    .set(JSON_WEB_KEY.UPDATE_TIME_MS, now)
                    .set(JSON_WEB_KEY.UPDATE_USER, auditUser)
                    .set(JSON_WEB_KEY.ENABLED, false)
                    .where(JSON_WEB_KEY.ID.eq(id))
                    // Already revoked is not an error, but it is not a revocation either.
                    .and(JSON_WEB_KEY.ENABLED.isTrue())
                    .execute();

            ensureActiveKey(context, now);
            return revoked;
        });
    }

    @Override
    public int revokeAll(final String auditUser) {
        final long now = System.currentTimeMillis();
        return JooqUtil.transactionResult(identityDbConnProvider, context -> {
            final int revoked = context
                    .update(JSON_WEB_KEY)
                    .set(JSON_WEB_KEY.VERSION, JSON_WEB_KEY.VERSION.plus(1))
                    .set(JSON_WEB_KEY.UPDATE_TIME_MS, now)
                    .set(JSON_WEB_KEY.UPDATE_USER, auditUser)
                    .set(JSON_WEB_KEY.ENABLED, false)
                    .where(JSON_WEB_KEY.ENABLED.isTrue())
                    .execute();

            ensureActiveKey(context, now);
            return revoked;
        });
    }

    /**
     * Leave the store with something to sign with, in the same transaction that took it away.
     * <p>
     * {@code listPublishable} would make one on the next request, but that leaves a moment where a node
     * asked to sign has no key, and invites two nodes to each make one. Doing it here means the store is
     * never observed without an active key.
     * </p>
     */
    private void ensureActiveKey(final DSLContext context, final long now) {
        if (fetchActiveKeys(context).isEmpty()) {
            insertActiveKey(context, now);
        }
    }

    /**
     * The active key: enabled, no expiry, newest first.
     */
    private JsonWebKeyRecord fetchActive(final DSLContext context) {
        return context
                .selectFrom(JSON_WEB_KEY)
                .where(JSON_WEB_KEY.ENABLED.isTrue())
                .and(JSON_WEB_KEY.EXPIRES_ON_MS.isNull())
                .orderBy(JSON_WEB_KEY.CREATE_TIME_MS.desc(), JSON_WEB_KEY.ID.desc())
                .limit(1)
                .fetchOne();
    }

    /**
     * Every active key, newest first. Normally one; more only after a lockless bootstrap race, which
     * {@link #rotate} then reconciles.
     */
    private List<JsonWebKeyRecord> fetchActiveKeys(final DSLContext context) {
        return context
                .selectFrom(JSON_WEB_KEY)
                .where(JSON_WEB_KEY.ENABLED.isTrue())
                .and(JSON_WEB_KEY.EXPIRES_ON_MS.isNull())
                .orderBy(JSON_WEB_KEY.CREATE_TIME_MS.desc(), JSON_WEB_KEY.ID.desc())
                .fetch();
    }

    /**
     * Deserialise a stored key, naming the row if it cannot be read.
     * <p>
     * The stored JSON contains the private key, so it is never included in the error - the key id and row id
     * are what let an operator find the offending record without the key itself reaching a log.
     * </p>
     */
    private PublicJsonWebKey toPublicJsonWebKey(final JsonWebKeyRecord record) {
        try {
            return jsonWebKeyFactory.fromJson(record.getJson());
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message(
                    "Unable to read the signing key in json_web_key row id {} (key_id '{}')",
                    record.getId(), record.getKeyId()), e);
        }
    }

    /**
     * Every publishable key: the active key plus any retired key not yet expired. Newest first.
     */
    private List<JsonWebKeyRecord> fetchPublishable(final DSLContext context, final long now) {
        return context
                .selectFrom(JSON_WEB_KEY)
                .where(JSON_WEB_KEY.ENABLED.isTrue())
                .and(JSON_WEB_KEY.EXPIRES_ON_MS.isNull().or(JSON_WEB_KEY.EXPIRES_ON_MS.gt(now)))
                .orderBy(JSON_WEB_KEY.CREATE_TIME_MS.desc(), JSON_WEB_KEY.ID.desc())
                .fetch();
    }

    private JsonWebKeyRecord insertActiveKey(final DSLContext context, final long now) {
        final PublicJsonWebKey publicJsonWebKey = jsonWebKeyFactory.createPublicKey();
        final int typeId = keyTypeDao.getTypeId(context, "JWK");

        // key_id must match the "kid" inside the json, so the column can be used to resolve or
        // withdraw a specific key. createPublicKey() sets the kid, so use it as the key_id too.
        final Integer id = context
                .insertInto(JSON_WEB_KEY)
                .columns(JSON_WEB_KEY.VERSION,
                        JSON_WEB_KEY.CREATE_TIME_MS,
                        JSON_WEB_KEY.CREATE_USER,
                        JSON_WEB_KEY.UPDATE_TIME_MS,
                        JSON_WEB_KEY.UPDATE_USER,
                        JSON_WEB_KEY.FK_TOKEN_TYPE_ID,
                        JSON_WEB_KEY.KEY_ID,
                        JSON_WEB_KEY.JSON,
                        JSON_WEB_KEY.ENABLED)
                .values(1,
                        now,
                        SYSTEM_AUDIT_USER,
                        now,
                        SYSTEM_AUDIT_USER,
                        typeId,
                        publicJsonWebKey.getKeyId(),
                        jsonWebKeyFactory.asJson(publicJsonWebKey),
                        true)
                // expires_on_ms deliberately left null: this is the active key until it is retired.
                .returning(JSON_WEB_KEY.ID)
                .fetchOne(JSON_WEB_KEY.ID);
        Objects.requireNonNull(id, "Null DB id");
        LOGGER.info(() -> LogUtil.message("Created active JSON web key {}", publicJsonWebKey.getKeyId()));

        // Re-fetch so we return what is actually in the DB.
        return context
                .selectFrom(JSON_WEB_KEY)
                .where(JSON_WEB_KEY.ID.eq(id))
                .fetchOne();
    }

    /**
     * Give a key an expiry, but only if it has none yet. The {@code expires_on_ms IS NULL} condition
     * makes this a compare-and-swap: exactly one caller can move a key out of the active state, so
     * concurrent rotations do not need a lock.
     *
     * @return 1 if this call retired the key, 0 if it was already retired by someone else.
     */
    private int casRetire(final DSLContext context,
                          final JsonWebKeyRecord key,
                          final long now,
                          final long expiresOnMs) {
        final int count = context
                .update(JSON_WEB_KEY)
                .set(JSON_WEB_KEY.VERSION, JSON_WEB_KEY.VERSION.plus(1))
                .set(JSON_WEB_KEY.UPDATE_TIME_MS, now)
                .set(JSON_WEB_KEY.UPDATE_USER, SYSTEM_AUDIT_USER)
                .set(JSON_WEB_KEY.EXPIRES_ON_MS, expiresOnMs)
                .where(JSON_WEB_KEY.ID.eq(key.getId()))
                .and(JSON_WEB_KEY.EXPIRES_ON_MS.isNull())
                .execute();
        if (count == 1) {
            LOGGER.info(() -> LogUtil.message("Retired JSON web key {}, published until {}",
                    key.getKeyId(), expiresOnMs));
        }
        return count;
    }

    private int deleteExpired(final DSLContext context, final long now) {
        final int deleted = context
                .deleteFrom(JSON_WEB_KEY)
                .where(JSON_WEB_KEY.EXPIRES_ON_MS.isNotNull())
                .and(JSON_WEB_KEY.EXPIRES_ON_MS.le(now))
                .execute();
        if (deleted > 0) {
            LOGGER.info(() -> LogUtil.message("Deleted {} expired JSON web key(s)", deleted));
        }
        return deleted;
    }
}
