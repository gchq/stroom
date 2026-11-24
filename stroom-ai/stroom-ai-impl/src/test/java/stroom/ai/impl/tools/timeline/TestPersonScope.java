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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.AgentKnowledge;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.timeline.PersonScope.By;
import stroom.ai.impl.tools.timeline.PersonScope.Scope;
import stroom.ai.impl.tools.timeline.PersonScope.Target;
import stroom.ai.impl.tools.timeline.TimelineFields.Origin;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Whose events are read in each index: the person by the person field, else the logon ids by the user field.
class TestPersonScope {

    private static final DocRef PROXY = new DocRef(ElasticIndexDoc.TYPE, "proxy-uuid", "Proxy");
    private static final ToolSession SESSION = ToolSession.chat(1);

    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final AgentKnowledge knowledge = new AgentKnowledge() {
        @Override
        public List<String> guideNames() {
            return List.of();
        }

        @Override
        public Optional<String> guide(final String name) {
            return Optional.empty();
        }

        @Override
        public List<String> conventionNames() {
            return List.of("ecs");
        }

        @Override
        public Optional<String> convention(final String name) {
            return Optional.of("field_map:\n  EventSource/User/Id: {name: user.name, type: keyword}\n"
                               + "  EventSource/User/UserDetails/Id: {name: user.id, type: keyword}\n"
                               + "  EventDetail/Authorise/User/Id: {name: user.target.name, type: keyword}\n"
                               + "  EventDetail/Authorise/User/UserDetails/Id: {name: user.target.id, type: "
                               + "keyword}\n");
        }
    };
    private final TimelineFields fields = new TimelineFields(agentInstructions, dataSourceAccess,
            Optional.of(knowledge),
                () -> Mockito.mock(DataSourceDocumentation.class));

    @Test
    void thePersonFieldIsFoundByConvention() {
        final Scope scope = resolve("P0042", List.of("alex.chen"), List.of(), Set.of("user.id", "user.name"));

        assertThat(scope.by()).isEqualTo(By.PERSON);
        assertThat(scope.field().field()).isEqualTo("user.id");
        assertThat(scope.field().origin()).isEqualTo(Origin.CONVENTION);
        assertThat(scope.values()).containsExactly("P0042");
        assertThat(By.PERSON.label()).isEqualTo("by person id");
    }

    @Test
    void theAgentsDocsPersonLineComesBeforeTheConvention() {
        Mockito.when(agentInstructions.fieldsRuled(SESSION, PROXY)).thenReturn(Map.of(InstructionRules.PERSON_FIELD,
                new FieldRule(List.of("EmployeeId"), "/Indexes")));

        final Scope scope = resolve("P0042", List.of(), List.of(), Set.of("EmployeeId", "user.id"));

        assertThat(scope.field().field()).isEqualTo("EmployeeId");
        assertThat(scope.field().origin()).isEqualTo(Origin.AGENTS);
    }

    @Test
    void theCallsPersonFieldComesFirst() {
        final Scope scope = PersonScope.resolve(fields, SESSION, PROXY, "P0042", List.of(), List.of("staff_no"),
                List.of(), Set.of("staff_no", "user.id")::contains);

        assertThat(scope.field().field()).isEqualTo("staff_no");
        assertThat(scope.field().origin()).isEqualTo(Origin.CALL);
    }

    @Test
    void anIndexWithNoPersonFieldFallsBackToTheLogonIds() {
        final Scope scope = resolve("P0042", List.of("alex.chen", " adm-achen ", "alex.chen", ""), List.of(),
                Set.of("user.name"));

        assertThat(scope.by()).isEqualTo(By.LOGON);
        assertThat(scope.field().field()).isEqualTo("user.name");
        // Each once, trimmed, in order.
        assertThat(scope.values()).containsExactly("alex.chen", "adm-achen");
        assertThat(scope.usable()).isTrue();
    }

    @Test
    void logonIdsAloneAreByLogonIdEvenWhereThereIsAPersonField() {
        final Scope scope = resolve(null, List.of("alex.chen"), List.of(), Set.of("user.id", "user.name"));

        assertThat(scope.by()).isEqualTo(By.LOGON);
        assertThat(scope.field().field()).isEqualTo("user.name");
    }

    @Test
    void anIndexWithNeitherIsLeftOutSayingWhy() {
        assertThat(resolve("P0042", List.of(), List.of(), Set.of("user.name")))
                .satisfies(scope -> {
                    assertThat(scope.usable()).isFalse();
                    assertThat(scope.leftOut()).contains("no field holding the person id", "no logon ids");
                });
        assertThat(resolve("P0042", List.of("alex.chen"), List.of(), Set.of("host.name")).leftOut())
                .isEqualTo("no field holding the user was found");
        assertThat(resolve(" ", List.of(), List.of(), Set.of("user.name")).leftOut())
                .isEqualTo("give a person id or one or more logon ids");
    }

    @Test
    void aPersonIdAloneWhereTheIndexHasOnlyAUserFieldSaysToGiveItAsALogonId() {
        // Chat 195: compare_activity with person ssato (a logon id) and userField user.name, but no logonIds.
        final Scope named = PersonScope.resolve(fields, SESSION, PROXY, "ssato", List.of(), List.of(),
                List.of("user.name"), Set.of("user.name", "host.name")::contains);
        assertThat(named.usable()).isFalse();
        assertThat(named.leftOut()).contains("no field holding the person id",
                "Its user field user.name holds logon ids: if 'ssato' is a logon id, call again with "
                + "logonIds: [\"ssato\"] (a person id isn't taken as a logon id unasked)");

        // A user field found by convention says so too.
        assertThat(resolve("ssato", List.of(), List.of(), Set.of("user.name")).leftOut())
                .contains("Its user field user.name holds logon ids", "logonIds: [\"ssato\"]");

        // No user field either: nothing to give logon ids for.
        assertThat(resolve("ssato", List.of(), List.of(), Set.of("host.name")).leftOut())
                .contains("no field holding the person id")
                .doesNotContain("logonIds");
    }

    @Test
    void closeVariantsDifferByDomainOrCaseOnly() {
        assertThat(PersonScope.variants(List.of("alex.chen", "adm-achen"),
                List.of("CORP\\alex.chen", "Alex.Chen@corp.example", "alex.chen", "alex.chang", "ADM-ACHEN",
                        "CORP\\alex.chen", " ")))
                .containsExactly("CORP\\alex.chen", "Alex.Chen@corp.example", "ADM-ACHEN");
        assertThat(PersonScope.variants(List.of(), List.of("alex.chen"))).isEmpty();
        assertThat(PersonScope.variants(List.of("alex.chen"), null)).isEmpty();
    }

    @Test
    void theTargetPersonFieldIsFoundByConventionTheActorsOwnActsLeftOut() {
        final Set<String> has = Set.of("user.id", "user.name", "user.target.id", "user.target.name");
        final Scope actor = resolve("P0042", List.of(), List.of(), has);

        final Target target = PersonScope.target(fields, SESSION, PROXY, "P0042", List.of(), List.of(), List.of(),
                actor, has::contains);

        assertThat(target.by()).isEqualTo(By.PERSON);
        assertThat(target.field().field()).isEqualTo("user.target.id");
        assertThat(target.field().origin()).isEqualTo(Origin.CONVENTION);
        assertThat(target.values()).containsExactly("P0042");
        assertThat(target.actorField()).isEqualTo("user.id");
        assertThat(target.actorValues()).containsExactly("P0042");
        assertThat(target.describe()).isEqualTo("done to them, by target person id (user.target.id from the field "
                                                + "convention (ecs); their own acts by user.id left out)");
    }

    @Test
    void theAgentsDocsTargetLineComesBeforeTheConventionAndTheCallsFirst() {
        Mockito.when(agentInstructions.fieldsRuled(SESSION, PROXY)).thenReturn(Map.of(
                InstructionRules.TARGET_PERSON_FIELD, new FieldRule(List.of("TargetEmployeeId"), "/Indexes")));
        final Set<String> has = Set.of("TargetEmployeeId", "user.target.id", "acted_on");

        assertThat(PersonScope.target(fields, SESSION, PROXY, "P0042", List.of(), List.of(), List.of(), null,
                has::contains)).satisfies(target -> {
                    assertThat(target.field().field()).isEqualTo("TargetEmployeeId");
                    assertThat(target.field().origin()).isEqualTo(Origin.AGENTS);
                    // No actor's scope: nothing left out.
                    assertThat(target.actorField()).isNull();
                });
        assertThat(PersonScope.target(fields, SESSION, PROXY, "P0042", List.of(), List.of("acted_on"), List.of(),
                null, has::contains).field().origin()).isEqualTo(Origin.CALL);
    }

    @Test
    void anIndexWithNoTargetPersonFieldFallsBackToTheTargetLogonField() {
        final Set<String> has = Set.of("user.name", "user.target.name");
        final Scope actor = resolve("P0042", List.of("alex.chen"), List.of(), has);

        final Target target = PersonScope.target(fields, SESSION, PROXY, "P0042", List.of("alex.chen"), List.of(),
                List.of(), actor, has::contains);

        assertThat(target.by()).isEqualTo(By.LOGON);
        assertThat(target.field().field()).isEqualTo("user.target.name");
        assertThat(target.values()).containsExactly("alex.chen");
        assertThat(target.actorField()).isEqualTo("user.name");
    }

    @Test
    void anIndexWithNoTargetFieldIsLeftOutSayingWhy() {
        final Set<String> has = Set.of("user.id", "user.name");

        assertThat(PersonScope.target(fields, SESSION, PROXY, "P0042", List.of(), List.of(), List.of(), null,
                has::contains)).satisfies(target -> {
                    assertThat(target.usable()).isFalse();
                    assertThat(target.leftOut()).contains("'target person:'", "no logon ids");
                    assertThat(target.describe()).isEqualTo(target.leftOut());
                });
        assertThat(PersonScope.target(fields, SESSION, PROXY, null, List.of("alex.chen"), List.of(), List.of(),
                null, has::contains).leftOut()).contains("'target user:'");
        assertThat(PersonScope.target(fields, SESSION, PROXY, " ", List.of(), List.of(), List.of(), null,
                has::contains).leftOut()).isEqualTo("give a person id or one or more logon ids");
    }

    private Scope resolve(final String person,
                          final List<String> logons,
                          final List<String> personFields,
                          final Set<String> indexFields) {
        return PersonScope.resolve(fields, SESSION, PROXY, person, logons, personFields, List.of(),
                indexFields::contains);
    }
}
