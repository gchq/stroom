/*
 * Copyright 2026 Crown Copyright
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
import stroom.security.identity.db.IdentityDbModule;
import stroom.security.identity.shared.SigningKeyRow;
import stroom.security.identity.shared.SigningKeyStatus;
import stroom.security.identity.token.JwkDao;
import stroom.security.identity.token.JwkFactoryImpl;
import stroom.security.identity.token.JwkRotationSummary;
import stroom.security.identity.token.KeyTypeDao;
import stroom.security.openid.api.JsonWebKeyFactory;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.jose4j.jwk.PublicJsonWebKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.security.identity.db.jooq.tables.JsonWebKey.JSON_WEB_KEY;

class TestJwkDaoImpl {

    // A rotation interval of zero means "the active key has always reached its rotation age", so
    // rotate() replaces it every call. Lets the tests force rotation without moving the clock.
    private static final Duration ROTATE_NOW = Duration.ZERO;
    private static final Duration NEVER_ROTATE = Duration.ofDays(365_000);
    private static final Duration LONG_RETENTION = Duration.ofDays(30);
    private static final Duration NO_RETENTION = Duration.ZERO;

    @Inject
    private JwkDao jwkDao;
    @Inject
    private IdentityDbConnProvider connProvider;
    @Inject
    private JsonWebKeyFactory jsonWebKeyFactory;

    @BeforeEach
    void beforeEach() {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new IdentityDbModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        // Bind only the two DAOs under test, not the whole IdentityDaoModule, whose
                        // other DAOs pull in search and security services this test does not need.
                        bind(JwkDao.class).to(JwkDaoImpl.class);
                        bind(KeyTypeDao.class).to(KeyTypeDaoImpl.class);
                        bind(JsonWebKeyFactory.class).to(JwkFactoryImpl.class);
                    }
                });
        injector.injectMembers(this);
        deleteAllKeys();
    }

    @AfterEach
    void afterEach() {
        deleteAllKeys();
    }

    @Test
    void emptyTable_createsExactlyOneActiveKey() {
        final PublicJsonWebKey active = jwkDao.getActiveKey();

        assertThat(active).isNotNull();
        assertThat(rowCount()).isEqualTo(1);
        assertThat(jwkDao.listPublishable())
                .extracting(PublicJsonWebKey::getKeyId)
                .containsExactly(active.getKeyId());
    }

    @Test
    void getActiveKey_isIdempotent() {
        final String first = jwkDao.getActiveKey().getKeyId();
        final String second = jwkDao.getActiveKey().getKeyId();

        assertThat(second).isEqualTo(first);
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void keyIdColumnMatchesKidInJson() {
        jwkDao.getActiveKey();

        final List<JooqRow> rows = fetchRows();
        assertThat(rows).hasSize(1);
        final JooqRow row = rows.getFirst();
        // The key_id column and the kid inside the json must agree, so the column can be used to
        // resolve or withdraw a specific key.
        assertThat(row.keyId()).isEqualTo(jsonWebKeyFactory.fromJson(row.json()).getKeyId());
    }

    @Test
    void allKeysRevoked_selfHeals() {
        final String revokedKid = jwkDao.getActiveKey().getKeyId();
        // Revoke every key, so there is no active key left to sign with.
        JooqUtil.context(connProvider, context -> context
                .update(JSON_WEB_KEY)
                .set(JSON_WEB_KEY.ENABLED, false)
                .execute());

        final PublicJsonWebKey healed = jwkDao.getActiveKey();

        assertThat(healed.getKeyId()).isNotEqualTo(revokedKid);
        // The revoked key must not be published or used to verify.
        assertThat(publishableKids()).containsExactly(healed.getKeyId());
    }

    @Test
    void rotate_replacesActiveAndRetainsOld() {
        final String oldKid = jwkDao.getActiveKey().getKeyId();

        final JwkRotationSummary summary = jwkDao.rotate(ROTATE_NOW, LONG_RETENTION);

        assertThat(summary.rotated()).isTrue();
        assertThat(summary.retiredKeyId()).isEqualTo(oldKid);
        final String newKid = summary.createdKeyId();
        assertThat(newKid).isNotEqualTo(oldKid);

        // Signing must use the new key, never the retired one.
        assertThat(jwkDao.getActiveKey().getKeyId()).isEqualTo(newKid);
        // Both are published so a token signed by the old key still verifies.
        assertThat(publishableKids()).containsExactlyInAnyOrder(oldKid, newKid);

        // The retired key has an expiry set; the active one does not.
        assertThat(expiryOf(oldKid)).isNotNull();
        assertThat(expiryOf(newKid)).isNull();
    }

    @Test
    void signingNeverPicksARetiredKey() {
        final String oldKid = jwkDao.getActiveKey().getKeyId();
        final String newKid = jwkDao.rotate(ROTATE_NOW, LONG_RETENTION).createdKeyId();

        // Even though both keys are published, the active key is always the newest non-expiring one.
        for (int i = 0; i < 5; i++) {
            assertThat(jwkDao.getActiveKey().getKeyId()).isEqualTo(newKid);
        }
        assertThat(jwkDao.getActiveKey().getKeyId()).isNotEqualTo(oldKid);
    }

    @Test
    void expiredKeyIsDroppedThenDeleted() {
        final String oldKid = jwkDao.getActiveKey().getKeyId();
        // Retire the old key with a real retention, so it is kept and published for now.
        final String newKid = jwkDao.rotate(ROTATE_NOW, LONG_RETENTION).createdKeyId();
        assertThat(publishableKids()).containsExactlyInAnyOrder(oldKid, newKid);

        // Simulate the retention having elapsed: the retired key's expiry is now in the past.
        setExpiry(oldKid, System.currentTimeMillis() - 1);

        // Expired, so it must drop out of the published set immediately, before it is deleted.
        assertThat(publishableKids()).containsExactly(newKid);

        // A rotation that does not itself rotate still deletes expired keys.
        final JwkRotationSummary summary = jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        assertThat(summary.rotated()).isFalse();
        assertThat(summary.deletedCount()).isEqualTo(1);
        assertThat(allKids()).containsExactly(newKid);
    }

    @Test
    void rotationNeverDeletesTheActiveKey() {
        // Even with zero retention on everything, the active key has a null expiry and so can never
        // be caught by the expired-key delete.
        jwkDao.getActiveKey();
        jwkDao.rotate(ROTATE_NOW, NO_RETENTION);
        final JwkRotationSummary summary = jwkDao.rotate(ROTATE_NOW, NO_RETENTION);

        assertThat(jwkDao.getActiveKey()).isNotNull();
        // There is always exactly one active (null-expiry) key.
        assertThat(activeKidCount()).isEqualTo(1);
        assertThat(summary.createdKeyId()).isNotNull();
    }

    @Test
    void concurrentFirstUse_isBenignAndReconcilable() throws InterruptedException {
        // Creation is lockless, so a race on an empty table may briefly make more than one active
        // key. That is deliberately harmless: every racer still gets a usable, verifiable key, and
        // whatever surplus is created must collapse back to one active key at the next rotation.
        final int threads = 16;
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch go = new CountDownLatch(1);
        final Set<String> kids = ConcurrentHashMap.newKeySet();
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    kids.add(jwkDao.getActiveKey().getKeyId());
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // Every racer got a key, and each is one of the keys actually persisted (none invented).
        assertThat(kids).isNotEmpty();
        assertThat(allKids()).containsAll(kids);

        // A rotation that does not itself rotate still reconciles any surplus back to one active key.
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        assertThat(activeKidCount()).isEqualTo(1);
    }

    @Test
    void concurrentRotate_createsExactlyOneReplacement() throws InterruptedException {
        // This is the property the whole lockless design rests on: many nodes running the rotation
        // job at once must produce exactly one replacement, via the compare-and-swap on retirement.
        final String oldKid = jwkDao.getActiveKey().getKeyId();
        // Age the active key past the rotation interval so it is due, while its replacement (created
        // "now") will not be, so the racers cannot cascade into rotating the replacement too.
        final Duration rotationInterval = Duration.ofDays(30);
        ageKey(oldKid, System.currentTimeMillis() - Duration.ofDays(60).toMillis());

        final int threads = 16;
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch go = new CountDownLatch(1);
        final Set<String> createdKids = ConcurrentHashMap.newKeySet();
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    final JwkRotationSummary summary = jwkDao.rotate(rotationInterval, LONG_RETENTION);
                    if (summary.createdKeyId() != null) {
                        createdKids.add(summary.createdKeyId());
                    }
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // Exactly one racer created a replacement...
        assertThat(createdKids).hasSize(1);
        final String newKid = createdKids.iterator().next();
        assertThat(newKid).isNotEqualTo(oldKid);
        // ...it is the one active key now...
        assertThat(activeKidCount()).isEqualTo(1);
        assertThat(jwkDao.getActiveKey().getKeyId()).isEqualTo(newKid);
        // ...and the old key was retired, not lost, so tokens it signed still verify.
        assertThat(publishableKids()).contains(oldKid, newKid);
    }

    // --- helpers ---------------------------------------------------------------------------------

    private record JooqRow(String keyId, String json, Long expiresOnMs, boolean enabled) {

    }

    private void deleteAllKeys() {
        JooqUtil.context(connProvider, context -> context.deleteFrom(JSON_WEB_KEY).execute());
    }

    private int rowCount() {
        return JooqUtil.contextResult(connProvider, context ->
                context.fetchCount(JSON_WEB_KEY));
    }

    private int activeKidCount() {
        return JooqUtil.contextResult(connProvider, context -> context
                .fetchCount(JSON_WEB_KEY, JSON_WEB_KEY.ENABLED.isTrue()
                        .and(JSON_WEB_KEY.EXPIRES_ON_MS.isNull())));
    }

    private List<JooqRow> fetchRows() {
        return JooqUtil.contextResult(connProvider, context -> context
                        .selectFrom(JSON_WEB_KEY)
                        .fetch())
                .map(r -> new JooqRow(r.getKeyId(), r.getJson(), r.getExpiresOnMs(), r.getEnabled()));
    }

    private Set<String> allKids() {
        return fetchRows().stream().map(JooqRow::keyId).collect(Collectors.toSet());
    }

    private Set<String> publishableKids() {
        return jwkDao.listPublishable().stream()
                .map(PublicJsonWebKey::getKeyId)
                .collect(Collectors.toSet());
    }

    private void setExpiry(final String kid, final long expiresOnMs) {
        JooqUtil.context(connProvider, context -> context
                .update(JSON_WEB_KEY)
                .set(JSON_WEB_KEY.EXPIRES_ON_MS, expiresOnMs)
                .where(JSON_WEB_KEY.KEY_ID.eq(kid))
                .execute());
    }

    private void ageKey(final String kid, final long createTimeMs) {
        JooqUtil.context(connProvider, context -> context
                .update(JSON_WEB_KEY)
                .set(JSON_WEB_KEY.CREATE_TIME_MS, createTimeMs)
                .where(JSON_WEB_KEY.KEY_ID.eq(kid))
                .execute());
    }

    private Long expiryOf(final String kid) {
        return fetchRows().stream()
                .filter(r -> r.keyId().equals(kid))
                .findFirst()
                .orElseThrow()
                .expiresOnMs();
    }

    @Test
    void revokingAKeyWithdrawsItFromEverythingThatTrustsKeys() {
        // The whole point: a withdrawn key must stop being published and stop being chosen to sign with.
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        final SigningKeyRow active = onlyRow();
        assertThat(active.getStatus()).isEqualTo(SigningKeyStatus.ACTIVE);

        assertThat(jwkDao.revoke(active.getId(), "tester")).isEqualTo(1);

        final SigningKeyRow reread = jwkDao.list().stream()
                .filter(row -> row.getId() == active.getId())
                .findFirst()
                .orElseThrow();
        assertThat(reread.getStatus()).isEqualTo(SigningKeyStatus.REVOKED);
        assertThat(jwkDao.listPublishable().stream().map(PublicJsonWebKey::getKeyId))
                .as("a revoked key must not be published")
                .doesNotContain(keyIdOf(active.getId()));
        assertThat(jwkDao.getActiveKey().getKeyId())
                .as("nor may it be chosen to sign with")
                .isNotEqualTo(keyIdOf(active.getId()));
    }

    @Test
    void revokingTheActiveKeyLeavesAReplacementInTheSameBreath() {
        // Waiting for the next request to make one leaves a window with nothing to sign with, and invites
        // two nodes to each make one.
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        final SigningKeyRow active = onlyRow();

        jwkDao.revoke(active.getId(), "tester");

        final List<SigningKeyRow> after = jwkDao.list();
        assertThat(after).hasSize(2);
        assertThat(after.stream().filter(row -> row.getStatus() == SigningKeyStatus.ACTIVE))
                .as("exactly one key must be signing, and it must not be the revoked one")
                .hasSize(1);
    }

    @Test
    void revokingEverythingLeavesExactlyOneNewKey() {
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        jwkDao.rotate(ROTATE_NOW, LONG_RETENTION);
        assertThat(jwkDao.list()).as("an active key and a retired one").hasSize(2);

        assertThat(jwkDao.revokeAll("tester")).isEqualTo(2);

        final List<SigningKeyRow> after = jwkDao.list();
        assertThat(after.stream().filter(row -> row.getStatus() == SigningKeyStatus.ACTIVE)).hasSize(1);
        assertThat(after.stream().filter(row -> row.getStatus() == SigningKeyStatus.REVOKED)).hasSize(2);
        assertThat(jwkDao.listPublishable()).as("only the replacement is trusted").hasSize(1);
    }

    @Test
    void revokingAnAlreadyRevokedKeyChangesNothing() {
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        final int id = onlyRow().getId();
        jwkDao.revoke(id, "tester");
        final int keyCount = jwkDao.list().size();

        assertThat(jwkDao.revoke(id, "tester")).as("nothing left to withdraw").isZero();
        assertThat(jwkDao.list()).as("and no further replacement made").hasSize(keyCount);
    }

    @Test
    void rotationDoesNotResurrectARevokedKey() {
        // Rotation reconciles surplus active keys and deletes expired ones; it must not undo a decision an
        // administrator made.
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        final int revokedId = onlyRow().getId();
        jwkDao.revoke(revokedId, "tester");

        jwkDao.rotate(ROTATE_NOW, LONG_RETENTION);

        assertThat(jwkDao.list().stream()
                .filter(row -> row.getId() == revokedId)
                .map(SigningKeyRow::getStatus))
                .containsExactly(SigningKeyStatus.REVOKED);
    }

    @Test
    void listedKeyDescribesNothingAboutTheKeyItself() {
        // The stored json is the private key. Asserting on the fields we expect would pass while material
        // sat in a field we forgot about, so this checks the whole serialised row.
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        final String storedJson = JooqUtil.contextResult(connProvider, context -> context
                .select(JSON_WEB_KEY.JSON)
                .from(JSON_WEB_KEY)
                .fetchOne(JSON_WEB_KEY.JSON));
        final String storedKeyId = keyIdOf(onlyRow().getId());

        final String rendered = jwkDao.list().toString();

        assertThat(rendered)
                .as("no part of the stored key may appear in what is handed out")
                .doesNotContain(storedJson)
                .doesNotContain(storedKeyId);
    }

    @Test
    void scheduledRetirementIsTellableFromAnAdministratorsRevocation() {
        // Both stop a key being used and both stamp update_user, so if they wrote the same value the audit
        // columns could not answer the question an incident actually asks: did this key lapse, or did
        // somebody pull it? "admin" was written for the scheduled case and is also a seeded account name,
        // so it read as a person having acted.
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);
        final int retiredId = onlyRow().getId();
        jwkDao.rotate(ROTATE_NOW, LONG_RETENTION);
        final int revokedId = jwkDao.list().stream()
                .map(SigningKeyRow::getId)
                .filter(id -> id != retiredId)
                .findFirst()
                .orElseThrow();
        jwkDao.revoke(revokedId, "jbloggs");

        assertThat(updateUserOf(retiredId))
                .as("retired on schedule, by nobody")
                .isEqualTo(InternalIdpProcessingUserIdentity.INTERNAL_PROCESSING_USER)
                .isNotEqualTo("admin");
        assertThat(updateUserOf(revokedId))
                .as("revoked deliberately, by a person")
                .isEqualTo("jbloggs");
    }

    @Test
    void createdKeyIsNotAttributedToAnyPerson() {
        // create_user is always the system here, so it carries no information - but written as "admin" it
        // carried misinformation, naming an account that can be signed into.
        jwkDao.rotate(NEVER_ROTATE, LONG_RETENTION);

        assertThat(createUserOf(onlyRow().getId()))
                .isEqualTo(InternalIdpProcessingUserIdentity.INTERNAL_PROCESSING_USER);
    }

    private String updateUserOf(final int id) {
        return JooqUtil.contextResult(connProvider, context -> context
                .select(JSON_WEB_KEY.UPDATE_USER)
                .from(JSON_WEB_KEY)
                .where(JSON_WEB_KEY.ID.eq(id))
                .fetchOne(JSON_WEB_KEY.UPDATE_USER));
    }

    private String createUserOf(final int id) {
        return JooqUtil.contextResult(connProvider, context -> context
                .select(JSON_WEB_KEY.CREATE_USER)
                .from(JSON_WEB_KEY)
                .where(JSON_WEB_KEY.ID.eq(id))
                .fetchOne(JSON_WEB_KEY.CREATE_USER));
    }

    private SigningKeyRow onlyRow() {
        final List<SigningKeyRow> rows = jwkDao.list();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private String keyIdOf(final int id) {
        return JooqUtil.contextResult(connProvider, context -> context
                .select(JSON_WEB_KEY.KEY_ID)
                .from(JSON_WEB_KEY)
                .where(JSON_WEB_KEY.ID.eq(id))
                .fetchOne(JSON_WEB_KEY.KEY_ID));
    }

}
