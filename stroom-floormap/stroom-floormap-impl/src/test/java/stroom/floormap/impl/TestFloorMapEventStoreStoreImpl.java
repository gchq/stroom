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

package stroom.floormap.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.planb.impl.PlanBNameValidator;
import stroom.planb.impl.PlanBStoreNames;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.TemporalStateSettings;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Severity;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The store's two rules: what a name may be, and that expiry may not outlive retention.
///
/// The second is the one worth pinning. Expiry hides an entity whose last event is older than the
/// cutoff; retention *deletes* data older than its own. Set expiry longer than retention and
/// the map silently under-reports — an entity idle beyond retention is dropped even though the expiry
/// rule says to show it, and a missing entity looks like a data problem rather than a configuration
/// one. Both settings live on this one document so that this check is possible at all.
class TestFloorMapEventStoreStoreImpl {

    // Another Plan B store type, standing in for an ordinary Plan B state store.
    private static final String PLAN_B_TYPE = "StateStore";

    private static SimpleDuration duration(final int time, final TimeUnit unit) {
        return SimpleDuration.builder().time(time).timeUnit(unit).build();
    }

    private static FloorMapEventStoreDoc doc(final SimpleDuration expiry,
                                             final RetentionSettings retention) {
        return FloorMapEventStoreDoc.eventStoreBuilder()
                .uuid("uuid")
                .name("events")
                .eventExpiry(expiry)
                .settings(new TemporalStateSettings.Builder().retention(retention).build())
                .build();
    }

    private static RetentionSettings retention(final boolean enabled, final SimpleDuration duration) {
        return new RetentionSettings.Builder()
                .enabled(enabled)
                .duration(duration)
                .build();
    }

    // ------------------------------------------------------------------
    // Expiry against retention.
    // ------------------------------------------------------------------

    @Test
    void expiryLongerThanRetentionIsRejected() {
        assertThat(FloorMapEventStoreStoreImpl.expiryExceedsRetention(
                doc(duration(30, TimeUnit.DAYS), retention(true, duration(7, TimeUnit.DAYS)))))
                .as("an entity idle for 10 days would be dropped despite a 30-day expiry")
                .isTrue();
    }

    @Test
    void expiryShorterThanRetentionIsAccepted() {
        assertThat(FloorMapEventStoreStoreImpl.expiryExceedsRetention(
                doc(duration(1, TimeUnit.HOURS), retention(true, duration(7, TimeUnit.DAYS)))))
                .isFalse();
    }

    @Test
    void expiryEqualToRetentionIsAccepted() {
        assertThat(FloorMapEventStoreStoreImpl.expiryExceedsRetention(
                doc(duration(7, TimeUnit.DAYS), retention(true, duration(7, TimeUnit.DAYS)))))
                .as("the boundary is the point at which the data still exists")
                .isFalse();
    }

    @Test
    void retentionThatIsOffConstrainsNothing() {
        // Nothing is deleted, so no expiry can outlive the data.
        assertThat(FloorMapEventStoreStoreImpl.expiryExceedsRetention(
                doc(duration(10, TimeUnit.YEARS), retention(false, duration(1, TimeUnit.HOURS)))))
                .isFalse();
    }

    @Test
    void noRetentionSettingsConstrainNothing() {
        assertThat(FloorMapEventStoreStoreImpl.expiryExceedsRetention(
                doc(duration(10, TimeUnit.YEARS), null)))
                .isFalse();
    }

    /// An unset expiry is still checked, against the default it resolves to.
    ///
    /// Otherwise the one document most likely to be misconfigured — a new one, left alone — is the
    /// one that escapes the check.
    @Test
    void anUnsetExpiryIsCheckedAgainstItsDefault() {
        assertThat(FloorMapEventStoreStoreImpl.expiryExceedsRetention(
                doc(null, retention(true, duration(1, TimeUnit.HOURS)))))
                .as("the default is 24 hours, which outlives a 1 hour retention")
                .isTrue();

        assertThat(FloorMapEventStoreStoreImpl.expiryExceedsRetention(
                doc(null, retention(true, duration(7, TimeUnit.DAYS)))))
                .isFalse();
    }

    // ------------------------------------------------------------------
    // Names, which ingest resolves rather than displays.
    // ------------------------------------------------------------------

    /// The store's names must satisfy Plan B's own pattern.
    ///
    /// `PlanBFilter` lowercases the map name and `DocFinder.findByName` is
    /// case-sensitive, so a capital makes the store permanently unreachable from a pipeline — with a
    /// single line in a stream's error log as the only symptom.
    @Test
    void namesFollowPlanBsPattern() {
        assertThat(PlanBNameValidator.isValidName("floor_map_events")).isTrue();
        assertThat(PlanBNameValidator.isValidName("events2")).isTrue();

        assertThat(PlanBNameValidator.isValidName("Floor Map Events")).isFalse();
        assertThat(PlanBNameValidator.isValidName("Events")).isFalse();
        assertThat(PlanBNameValidator.isValidName("floor-map-events")).isFalse();
        assertThat(PlanBNameValidator.isValidName("")).isFalse();
    }

    // ------------------------------------------------------------------
    // Copy naming.
    // ------------------------------------------------------------------

    @Test
    void copyTakesTheNextFreeNumber() {
        assertThat(FloorMapEventStoreStoreImpl.createUniqueName("events", Set.of("events")))
                .isEqualTo("events2");
        assertThat(FloorMapEventStoreStoreImpl.createUniqueName("events2", Set.of("events", "events2")))
                .isEqualTo("events3");
        assertThat(FloorMapEventStoreStoreImpl.createUniqueName(
                "events", Set.of("events", "events2", "events3")))
                .isEqualTo("events4");
    }

    @Test
    void copyNameStaysWithinThePatternAndTheLengthLimit() {
        final String longName = "a".repeat(60);
        final String copy = FloorMapEventStoreStoreImpl.createUniqueName(longName, Set.of(longName));

        assertThat(copy).matches(PlanBNameValidator.getPattern());
        assertThat(copy.length()).isLessThanOrEqualTo(48);
    }

    // ------------------------------------------------------------------
    // Name uniqueness across Plan B document types.
    // ------------------------------------------------------------------

    /// Regression test: names were only checked against other event stores, but Plan B resolves a
    /// name across every Plan B document type, so sharing a name with an ordinary Plan B store broke
    /// lookups of both.
    @Test
    void createRefusesANamePlanBStoreHas() {
        final RealStore realStore = new RealStore();
        realStore.givenAPlanBStoreNamed("events");

        assertThatThrownBy(() -> realStore.store.createDocument("events"))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("events");
        assertThat(realStore.store.list())
                .as("the rolled-back document must not be left behind")
                .isEmpty();
    }

    @Test
    void renameRefusesANamePlanBStoreHas() {
        final RealStore realStore = new RealStore();
        final DocRef docRef = realStore.store.createDocument("mine");
        realStore.givenAPlanBStoreNamed("events");

        assertThatThrownBy(() -> realStore.store.renameDocument(docRef, "events"))
                .isInstanceOf(EntityServiceException.class);
    }

    /// Regression test: a copy given a unique name was only made unique among event stores, so the
    /// usual explorer copy could produce a name a Plan B store already had.
    @Test
    void aCopyIsNamedClearOfPlanBStores() {
        final RealStore realStore = new RealStore();
        final DocRef docRef = realStore.store.createDocument("events");
        Mockito.when(realStore.docFinder.findByName(PLAN_B_TYPE, "*", true))
                .thenReturn(List.of(new DocRef(PLAN_B_TYPE, "plan-b-uuid", "events2")));

        final DocRef copy = realStore.store.copyDocument(docRef, "events", true, Set.of());

        assertThat(copy.getName()).isEqualTo("events3");
    }

    /// Regression test: names were checked only among the event stores the user could see, but a
    /// store hidden from them breaks name resolution just the same.
    @Test
    void anEventStoreTheUserCannotSeeStillHasItsName() {
        final RealStore realStore = new RealStore();
        realStore.store.createDocument("events");
        realStore.securityContext.canView = false;

        assertThatThrownBy(() -> realStore.store.createDocument("events"))
                .isInstanceOf(EntityServiceException.class);
    }

    /// Regression test: import did not check names at all.
    @Test
    void importRefusesANewEventStoreWithANamePlanBStoreHas() {
        final RealStore realStore = new RealStore();
        realStore.givenAPlanBStoreNamed("events");
        final DocRef imported = new DocRef(FloorMapEventStoreDoc.TYPE, "new-uuid", "events");
        final ImportState importState = new ImportState(imported, "events");

        realStore.store.importDocument(imported, null, importState, ImportSettings.auto());

        assertThat(importState.getMessageList())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getSeverity()).isEqualTo(Severity.ERROR);
                    assertThat(message.getMessage()).contains("events");
                });
    }

    /// An existing store keeps its own name on import, so re-importing it never clashes with itself.
    @Test
    void importOfAnExistingEventStoreIsNotRefused() {
        final RealStore realStore = new RealStore();
        final DocRef existing = realStore.store.createDocument("events");
        final ImportState importState = new ImportState(existing, "events");

        realStore.store.importDocument(existing, null, importState, ImportSettings.auto());

        assertThat(importState.getMessageList()).isEmpty();
    }

    // The real store over an in-memory persistence, with the other Plan B store types answered by a
    // mock finder.
    private static class RealStore {

        private final DocFinder docFinder = Mockito.mock(DocFinder.class);
        private final ToggleableSecurityContext securityContext = new ToggleableSecurityContext();
        private final FloorMapEventStoreStoreImpl store = new FloorMapEventStoreStoreImpl(
                new StoreFactoryImpl(new MemoryPersistence(), null, securityContext, null, () -> null),
                new FloorMapEventStoreSerialiser(new Serialiser2FactoryImpl()),
                securityContext,
                () -> null,
                new PlanBStoreNames(securityContext,
                        () -> docFinder,
                        Set.of(PLAN_B_TYPE, FloorMapEventStoreDoc.TYPE)));

        private void givenAPlanBStoreNamed(final String name) {
            Mockito.when(docFinder.findByName(PLAN_B_TYPE, name))
                    .thenReturn(List.of(new DocRef(PLAN_B_TYPE, "plan-b-uuid", name)));
        }
    }

    // A security context whose user can be made unable to see any document.
    private static class ToggleableSecurityContext extends MockSecurityContext {

        private boolean canView = true;

        @Override
        public boolean hasDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
            return canView;
        }
    }
}
