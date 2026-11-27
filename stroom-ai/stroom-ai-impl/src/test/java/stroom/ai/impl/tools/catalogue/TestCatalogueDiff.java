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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.Condition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What changed in a catalogue since the one promoted with the feed's current version.
class TestCatalogueDiff {

    private static final EventType LOGON = type("Authenticate-Logon", "Logon", "login");
    private static final EventType LOGOFF = type("Authenticate-Logoff", "Logoff", "logout");

    @Test
    void eventTypesAddedChangedAndRemovedAreSaidByTypeId() {
        final Catalogue promoted = new Catalogue(List.of(LOGON, LOGOFF), Catalogue.CatchAll.CONFIRMED, null);
        final EventType signin = type("Authenticate-Signin", "Logon", "login");
        final EventType logoffChanged = new EventType(LOGOFF.typeId(), LOGOFF.title(), "A user signed out",
                LOGOFF.eventDetail(), LOGOFF.action(), null, null, null, null, List.of(way("logout"), way("exit")),
                false, LOGOFF.basis(), LOGOFF.confidence(), null, EventType.CONFIRMED);
        final Catalogue current = new Catalogue(List.of(logoffChanged, signin), Catalogue.CatchAll.PROPOSED, null);

        final CatalogueDiff.Diff diff = CatalogueDiff.between(promoted, current);

        assertThat(diff.added()).containsExactly("Authenticate-Signin");
        assertThat(diff.removed()).containsExactly("Authenticate-Logon");
        assertThat(diff.changed()).containsExactly(new CatalogueDiff.Changed("Authenticate-Logoff",
                List.of("recognised by", "description")));
        assertThat(diff.none()).isFalse();
        assertThat(diff.said()).containsExactly("added: Authenticate-Signin",
                "changed: Authenticate-Logoff (recognised by, description)", "removed: Authenticate-Logon");
        assertThat(diff.toJson()).containsEntry("added", List.of("Authenticate-Signin"))
                .containsEntry("removed", List.of("Authenticate-Logon"))
                .doesNotContainKey("catch_all");
    }

    @Test
    void theSameCatalogueStatusAndBasisApartIsNoChange() {
        final Catalogue promoted = new Catalogue(List.of(LOGON, LOGOFF), Catalogue.CatchAll.CONFIRMED, null);
        final EventType proposed = new EventType(LOGON.typeId(), LOGON.title(), null, LOGON.eventDetail(),
                LOGON.action(), null, null, null, null, LOGON.recognisedBy(), false, EventType.AGENT_KNOWLEDGE, "low",
                null, EventType.PROPOSED);

        final CatalogueDiff.Diff diff = CatalogueDiff.between(promoted, new Catalogue(List.of(proposed, LOGOFF),
                null, null));

        assertThat(diff.none()).isTrue();
        assertThat(diff.said()).isEmpty();
    }

    @Test
    void noPromotedCatalogueMakesEveryEventTypeAddedAndTheCatchAllsOwnTranslationIsAChange() {
        final Catalogue current = new Catalogue(List.of(LOGON), new Catalogue.CatchAll(EventType.CONFIRMED, null,
                new Catalogue.CatchAll.Translation("Authenticate-Other", "Authenticate", "Logon", null)), null);

        final CatalogueDiff.Diff diff = CatalogueDiff.between(null, current);

        assertThat(diff.added()).containsExactly("Authenticate-Logon");
        assertThat(diff.catchAllChanged()).isTrue();
        assertThat(diff.toJson()).containsEntry("catch_all", "changed");
        assertThat(CatalogueDiff.between(current, null).removed()).containsExactly("Authenticate-Logon");
    }

    private static EventType type(final String typeId, final String action, final String value) {
        return new EventType(typeId, typeId, null, "Authenticate", action, null, null, null, null, List.of(way(value)),
                false, EventType.USER_CONFIRMED, "high", null, EventType.CONFIRMED);
    }

    private static Way way(final String value) {
        return new Way(List.of(Condition.fromJson(Map.of("field", "action", "equals", value))), List.of());
    }
}
