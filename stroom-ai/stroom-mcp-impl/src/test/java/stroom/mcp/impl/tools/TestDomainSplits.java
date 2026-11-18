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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.draft.LocalCheck.QualifiedIds;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDomainSplits {

    @SuppressWarnings("unchecked")
    private static final TranslationMapping MAPPING = TranslationMapping.fromJson(
            (Map<String, Object>) ToolJson.loads("""
            {"input": "json",
             "common": [{"path": "EventSource/User/Id", "field": "by_user"},
                        {"path": "EventSource/System/Name", "value": "Acme"}],
             "events": [{"name": "login", "fields": [
                          {"path": "EventDetail/TypeId", "value": "Login"},
                          {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                          {"path": "EventDetail/Authenticate/User/Id", "field": "by_user"},
                          {"path": "EventDetail/Authenticate/User/Domain", "value": "ACME"}]},
                        {"name": "other", "fields": [
                          {"path": "EventDetail/TypeId", "value": "Other"},
                          {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                          {"path": "EventDetail/Authenticate/User/Id", "field": "upn"}]}]}"""));
    private static final QualifiedIds BY_USER = new QualifiedIds("by_user",
            List.of("EventSource/User/Id", "EventDetail/Authenticate/User/Id"), List.of("domain.com\\sam.lee"));

    @Test
    void theQuestionShowsWhatTheSplitGives() {
        assertThat(DomainSplits.question(List.of(BY_USER))).isEqualTo("Split the domain off the users' Ids from "
                                                                     + "'by_user' (EventSource/User/Id, "
                                                                     + "EventDetail/Authenticate/User/Id)? E.g. "
                                                                     + "domain.com\\sam.lee: the Id sam.lee, the "
                                                                     + "Domain domain.com.");
        assertThat(DomainSplits.question(List.of(new QualifiedIds("upn", List.of("EventSource/User/Id"),
                List.of("sam.lee@acme.example")), BY_USER)))
                .startsWith("Split the domain off the users' Ids from 'upn' (EventSource/User/Id), 'by_user' (")
                .endsWith("the Id sam.lee, the Domain acme.example.");
        // Another Id the field goes into is asked about again.
        assertThat(DomainSplits.key(BY_USER)).isEqualTo("by_user -> EventSource/User/Id, "
                                                        + "EventDetail/Authenticate/User/Id");
    }

    @Test
    void eachIdIsStrippedAndADomainAddedWhereNoneIsMapped() {
        final DomainSplits.Split split = DomainSplits.split(MAPPING, BY_USER, TranslationWorld.SCHEMA);

        final String json = JsonUtil.writeValueAsString(split.mapping().toJson(DumpMode.COMPACT), false);
        assertThat(json).contains(
                "{\"path\":\"EventSource/User/Id\",\"field\":\"by_user\",\"transform\":\"strip_domain\"},"
                + "{\"path\":\"EventSource/User/Domain\",\"field\":\"by_user\",\"transform\":\"domain\"}",
                "{\"path\":\"EventDetail/Authenticate/User/Id\",\"field\":\"by_user\",\"transform\":\"strip_domain\"}",
                // The rule's own Domain is kept, and another field's Id left alone.
                "{\"path\":\"EventDetail/Authenticate/User/Domain\",\"value\":\"ACME\"}",
                "{\"path\":\"EventDetail/Authenticate/User/Id\",\"field\":\"upn\"}");
        assertThat(split.done()).containsExactly(
                "EventSource/User/Id: strip_domain, and EventSource/User/Domain from 'by_user' (domain)",
                "EventDetail/Authenticate/User/Id: strip_domain");
    }

    @Test
    void aSplitIsUndoneAsTheUserSaid() {
        final DomainSplits.Split split = DomainSplits.split(MAPPING, BY_USER, TranslationWorld.SCHEMA);

        final DomainSplits.Split unsplit = DomainSplits.unsplit(split.mapping());

        final String json = JsonUtil.writeValueAsString(unsplit.mapping().toJson(DumpMode.COMPACT), false);
        assertThat(json).doesNotContain("strip_domain", "\"transform\":\"domain\"")
                .contains("{\"path\":\"EventSource/User/Id\",\"field\":\"by_user\"}",
                        // A Domain the rule gives itself is no split's.
                        "{\"path\":\"EventDetail/Authenticate/User/Domain\",\"value\":\"ACME\"}");
        assertThat(unsplit.done()).containsExactly("EventSource/User/Id: written whole",
                "EventSource/User/Domain: taken out", "EventDetail/Authenticate/User/Id: written whole");
        // Nothing split: nothing done.
        assertThat(DomainSplits.unsplit(MAPPING).done()).isEmpty();
        assertThat(DomainSplits.unsplit(MAPPING).mapping()).isSameAs(MAPPING);
    }

    @Test
    void theUsersWordIsSplitOrKeep() {
        assertThat(DomainSplits.answerOf(null)).isEmpty();
        assertThat(DomainSplits.answerOf(" ")).isEmpty();
        assertThat(DomainSplits.answerOf("split")).contains(DomainSplits.SPLIT);
        assertThat(DomainSplits.answerOf("Keep as is")).contains(DomainSplits.KEEP);
        assertThat(DomainSplits.answerOf("keep")).contains(DomainSplits.KEEP);
        assertThatThrownBy(() -> DomainSplits.answerOf("maybe")).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("'Split' or 'Keep as is'");
    }

    @Test
    void changesRemovingASplitsDomainUndoIt() {
        assertThat(DomainSplits.undoneIn(Map.of("common", List.of(Map.of("path", "EventSource/User/Domain",
                "remove", true))), BY_USER)).isTrue();
        assertThat(DomainSplits.undoneIn(Map.of("events", List.of(Map.of("name", "login", "fields", List.of(
                Map.of("path", "EventDetail/Authenticate/User/Domain", "remove", true))))), BY_USER)).isTrue();
        // A new rule writing the Id whole is no answer; nor is another Domain removed.
        assertThat(DomainSplits.undoneIn(Map.of("events", List.of(Map.of("name", "new", "fields", List.of(
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "by_user"))))), BY_USER)).isFalse();
        assertThat(DomainSplits.undoneIn(Map.of("common", List.of(Map.of("path", "EventDetail/Copy/User/Domain",
                "remove", true))), BY_USER)).isFalse();
        assertThat(DomainSplits.undoneIn(null, BY_USER)).isFalse();
    }

    @Test
    void aKeptMappingAnswersByHowItWritesTheFieldsIds() {
        final Map<String, Object> whole = MAPPING.toJson(DumpMode.COMPACT);
        final Map<String, Object> split = DomainSplits.split(MAPPING, BY_USER, TranslationWorld.SCHEMA).mapping()
                .toJson(DumpMode.COMPACT);

        assertThat(DomainSplits.answersIn(whole, Set.of("by_user"))).containsExactly(DomainSplits.KEEP);
        assertThat(DomainSplits.answersIn(split, Set.of("by_user"))).containsExactly(DomainSplits.SPLIT);
        // upn's Id is whole, by_user's split: both.
        assertThat(DomainSplits.answersIn(split, Set.of("by_user", "upn"))).containsExactlyInAnyOrder(
                DomainSplits.SPLIT, DomainSplits.KEEP);
        assertThat(DomainSplits.answersIn(whole, Set.of("other"))).isEmpty();
        assertThat(DomainSplits.answersIn(null, Set.of("by_user"))).isEmpty();
    }
}
