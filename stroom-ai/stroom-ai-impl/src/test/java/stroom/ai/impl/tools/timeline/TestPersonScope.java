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
import stroom.ai.impl.tools.timeline.PersonScope.By;
import stroom.ai.impl.tools.timeline.PersonScope.Scope;
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
                               + "  EventSource/User/UserDetails/Id: {name: user.id, type: keyword}\n");
        }
    };
    private final TimelineFields fields = new TimelineFields(agentInstructions, dataSourceAccess,
            Optional.of(knowledge));

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
    void closeVariantsDifferByDomainOrCaseOnly() {
        assertThat(PersonScope.variants(List.of("alex.chen", "adm-achen"),
                List.of("CORP\\alex.chen", "Alex.Chen@corp.example", "alex.chen", "alex.chang", "ADM-ACHEN",
                        "CORP\\alex.chen", " ")))
                .containsExactly("CORP\\alex.chen", "Alex.Chen@corp.example", "ADM-ACHEN");
        assertThat(PersonScope.variants(List.of(), List.of("alex.chen"))).isEmpty();
        assertThat(PersonScope.variants(List.of("alex.chen"), null)).isEmpty();
    }

    private Scope resolve(final String person,
                          final List<String> logons,
                          final List<String> personFields,
                          final Set<String> indexFields) {
        return PersonScope.resolve(fields, SESSION, PROXY, person, logons, personFields, List.of(),
                indexFields::contains);
    }
}
