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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedDocumentation {

    static final String INDEX_DOC = """
            # Acme VPN - indexing

            ## Purpose and data

            Indexes the Acme VPN Events into ecs-vpn-v1.

            ## Field mapping

            Documents for `ecs-vpn-v1` (elasticsearch); the time field is `@timestamp`.

            | Index field | Description | Type | From (event-logging path) | In sample | Sample values |
            | --- | --- | --- | --- | --- | --- |
            | `User.DomainName` | The domain of the user's account, e.g. CORP. | keyword | \
            `EventDetail/*/User/Domain` | 80% of events | `corp.example.com` |
            | `User.Id` | The user. | keyword | `EventDetail/*/User/Id` | always | `bob` |
            | `Note` | A bar \\| in it.<br>Two lines. | text | `EventDetail/Description` | always | `x` |

            ## Version control
            """;

    static final String EVENTS_DOC = """
            # Acme VPN

            ## Purpose and data

            Logon and logoff records from the Acme VPN concentrators, as JSON lines.

            ## Field mapping

            | XPath | Description | From |
            | --- | --- | --- |
            | `EventSource/Device/HostName` | The device. | host |

            ### Event types

            | Rule | TypeId | Description | EventDetail |
            | --- | --- | --- | --- |
            | **logon**<br>action = 'LOGIN' | Logon | | `Authenticate/Action` <- Logon<br>\
            `Authenticate/User/Domain` <- realm<br>`Authenticate/User/Id` <- user |
            | **logoff**<br>action = 'LOGOUT' | Logoff | | `Authenticate/User/Domain` <- realm |
            """;

    @Test
    void testNamesCompareWhateverTheirSeparatorsAndCase() {
        assertThat(FeedDocumentation.norm("User.DomainName"))
                .isEqualTo(FeedDocumentation.norm("user_domain_name"))
                .isEqualTo(FeedDocumentation.norm("UserDomainName"))
                .isEqualTo("userdomainname");
    }

    @Test
    void testTablesAndSectionsAreReadBackFromTheGeneratedMarkdown() {
        assertThat(FeedDocumentation.section(EVENTS_DOC, "Purpose and data"))
                .isEqualTo("Logon and logoff records from the Acme VPN concentrators, as JSON lines.");
        assertThat(FeedDocumentation.section(EVENTS_DOC, "Field mapping")).contains("### Event types");
        final List<List<Map<String, String>>> tables = FeedDocumentation.tables(
                FeedDocumentation.section(INDEX_DOC, "Field mapping"));
        assertThat(tables).hasSize(1);
        final List<Map<String, String>> first = tables.getFirst();
        assertThat(first.get(0).get("Index field")).isEqualTo("`User.DomainName`");
        assertThat(first.get(0).get("Type")).isEqualTo("keyword");
        // Escaped bars and <br>s undone.
        assertThat(first.get(2).get("Description")).isEqualTo("A bar | in it.\nTwo lines.");
    }

    @Test
    void testAFieldsRowsAreFoundInTheIndexDocAndThroughItsSourceInTheEventsDoc() {
        // The user's question: "what is the User.DomainName field about?"
        final List<Map<String, String>> indexRows = FeedDocumentation.rowsAbout(INDEX_DOC, "User.DomainName",
                List.of());
        assertThat(indexRows).extracting(row -> row.get("Index field")).containsExactly("`User.DomainName`");
        assertThat(indexRows.getFirst().get("From (event-logging path)")).isEqualTo("`EventDetail/*/User/Domain`");
        // The events doc names the path without EventDetail/ in each rule's lines: both rules' lines are found,
        // and only those lines, not the rule's other fields.
        final List<Map<String, String>> eventRows = FeedDocumentation.rowsAbout(EVENTS_DOC, "User.DomainName",
                List.of("EventDetail/*/User/Domain"));
        assertThat(eventRows).extracting(row -> row.get("EventDetail"))
                .containsExactly("`Authenticate/User/Domain` <- realm", "`Authenticate/User/Domain` <- realm");
        assertThat(eventRows.getFirst().get("Rule")).startsWith("**logon**");
        // A path given as the field: the EventSource table's row.
        assertThat(FeedDocumentation.rowsAbout(EVENTS_DOC, "EventSource/Device/HostName", List.of()))
                .extracting(row -> row.get("XPath"))
                .containsExactly("`EventSource/Device/HostName`");
        assertThat(FeedDocumentation.rowsAbout(INDEX_DOC, "Nothing.Here", List.of())).isEmpty();
    }

    @Test
    void testSearchAdviceFollowsWhatStroomWasFoundToDoForEachType() {
        final String keyword = String.join(" ", FeedDocumentation.searchAdvice("elasticsearch", "keyword", "",
                true));
        assertThat(keyword).contains("EQUALS '*x'", "STARTS_WITH and CONTAINS find nothing", "case-sensitive");
        assertThat(String.join(" ", FeedDocumentation.searchAdvice("elasticsearch", "ip", "", true)))
                .contains("CIDR");
        assertThat(String.join(" ", FeedDocumentation.searchAdvice("elasticsearch", "text", "", true)))
                .contains("keyword sub-field");
        final String lucene = String.join(" ", FeedDocumentation.searchAdvice("lucene", "text", "KEYWORD", true));
        assertThat(lucene).contains("EQUALS '*x'", "ENDS_WITH");
        assertThat(String.join(" ", FeedDocumentation.searchAdvice("lucene", "text", "ALPHA_NUMERIC", true)))
                .contains("ranges");
        assertThat(String.join(" ", FeedDocumentation.searchAdvice("lucene", "date", "", true)))
                .contains("BETWEEN");
        assertThat(String.join(" ", FeedDocumentation.searchAdvice("lucene", "keyword", "", false)))
                .contains("not case-sensitive");
    }

    @Test
    void testTheEventKindsComeFromTheKeptMapping() {
        final Map<String, Object> payload = Map.of(
                "input", "data_splitter",
                "unmatched", "skip",
                "common", List.of(Map.of("path", "EventTime/TimeCreated", "field", "time")),
                "events", List.of(
                        Map.of("name", "logon",
                                "when", List.of(Map.of("field", "action", "equals", "LOGIN")),
                                "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"))),
                        Map.of("name", "noise", "drop", true,
                                "when", List.of(Map.of("field", "action", "equals", "PING")))));
        final List<String> kinds = FeedDocumentation.eventKinds(payload);
        assertThat(kinds.get(0)).startsWith("logon: Authenticate (").contains("LOGIN");
        assertThat(kinds.get(1)).startsWith("noise: left untranslated");
        assertThat(FeedDocumentation.eventKinds(Map.of("events", "not a list"))).isEmpty();
    }

    @Test
    void testAMisrememberedFieldIsAnsweredWithTheFieldsSharingItsWords() {
        assertThat(FeedDocumentation.words("User.DomainName"))
                .isEqualTo(FeedDocumentation.words("user_domain_name"))
                .isEqualTo(FeedDocumentation.words("userDomainName"))
                .isEqualTo(Set.of("user", "domain", "name"));
        assertThat(FeedDocumentation.words("IPAddress")).isEqualTo(Set.of("ip", "address"));
        final List<String> fields = List.of("StreamId", "UserId", "AuthenticateUserId", "UserDomain", "HostName");
        assertThat(FeedDocumentation.similar("User.DomainName", fields))
                .containsExactly("UserDomain", "UserId", "AuthenticateUserId", "HostName");
        assertThat(FeedDocumentation.similar("Nothing", fields)).isEmpty();
    }

    @Test
    void testAFieldMentionedOutsideTheTablesIsFound() {
        assertThat(FeedDocumentation.mentions("Extracted fields: realm, user.\n| realm | x |\n# realm", "realm"))
                .containsExactly("Extracted fields: realm, user.");
    }

    @Test
    void testAPlanSourceNamesTheEventPathsItsWildcardsCover() {
        assertThat(FeedDocumentation.sourceMatches("EventDetail/*/User/Id", "EventDetail/Authenticate/User/Id"))
                .isTrue();
        assertThat(FeedDocumentation.sourceMatches("EventDetail//Object/Name", "EventDetail/Export/Source/Object/Name"))
                .isTrue();
        assertThat(FeedDocumentation.sourceMatches("EventDetail/*/User/Id", "EventDetail/A/B/User/Id")).isFalse();
    }
}
