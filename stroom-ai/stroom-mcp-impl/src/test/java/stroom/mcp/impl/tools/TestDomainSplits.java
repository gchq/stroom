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

import stroom.ai.impl.tools.draft.LocalCheck.QualifiedIds;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDomainSplits {

    @SuppressWarnings("unchecked")
    private static final TranslationMapping MAPPING = TranslationMapping.fromJson(
            (Map<String, Object>) PyJson.loads("""
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
}
