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

package stroom.ai.impl.tools.generation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Groups (for_each with fields): an element written once per item, each holding the fields read from that item;
/// from each source (split, array, zip), nested, as Data, and the mappings refused.
class TestXsltGeneratorGroups extends XsltRuns {

    // A stroom:log that writes what it logs into the output, so a test sees it.
    private static final String LOGGING_STUB = "<xsl:function name=\"stroom:log\"><xsl:param name=\"level\"/>"
                                               + "<xsl:param name=\"message\"/><xsl:element name=\"logged\">"
                                               + "<xsl:attribute name=\"level\" select=\"$level\"/>"
                                               + "<xsl:value-of select=\"$message\"/></xsl:element></xsl:function>"
                                               + "<xsl:function name=\"stroom:record-no\"><xsl:sequence select=\"7\"/>"
                                               + "</xsl:function>";

    private static Map<String, Object> entry(final String text) {
        return json(text);
    }

    private static XsltGenerator.Generated generate(final TranslationMapping mapping) {
        return XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
    }

    // A send rule with these fields after its TypeId and sender.
    private static TranslationMapping sendMapping(final String input, final Map<?, ?>... fields) {
        final List<Object> all = new ArrayList<>(List.of(
                entry("""
                        {"path": "EventDetail/TypeId", "value": "Send"}"""),
                entry("""
                        {"path": "EventDetail/Send/Source/User/Id", "field": "sender"}""")));
        all.addAll(List.of(fields));
        final List<Map<String, Object>> common = "json".equals(input)
                ? plus(BASE.subList(0, 4), entry("""
                {"path": "EventSource/Device/HostName", "field": "host"}"""))
                : BASE;
        return mapping(with("input", input, "common", common, "events", List.of(rule("send", List.of(), all))));
    }

    private static List<String> users(final XmlElement event) {
        final XmlElement destination = find(event, "EventDetail/Send/Destination");
        return destination == null
                ? List.of()
                : children(destination, "User").stream()
                        .map(user -> text(user, "Id") + "|" + text(user, "Domain"))
                        .toList();
    }

    @Test
    void anArrayOfObjectsGivesAUserPerObjectEachWithItsOwnKeys() {
        final TranslationMapping mapping = sendMapping("json", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "recipients"}, "as": "recipient",
                 "fields": [{"path": "Id", "field": "address.local"}, {"path": "Domain", "field": "address.domain"},
                            {"path": "Data", "data_name": "status", "field": "status"}]}"""));
        final XsltGenerator.Generated result = generate(mapping);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>\
                <string key="sender">jane</string>
                <array key="recipients">\
                <map><map key="address"><string key="local">john</string><string key="domain">mail.com</string></map>\
                <string key="status">sent</string></map>\
                <map><map key="address"><string key="local">jack</string><string key="domain">gmail.com</string></map>\
                </map></array>
                </map><map>
                <string key="time">2026-09-28T10:01:00.000Z</string><string key="host">h</string>\
                <string key="sender">jane</string>
                <map key="recipients"><map key="address"><string key="local">solo</string>\
                <string key="domain">x.org</string></map></map>
                </map></array>""");

        assertThat(invalid(events, XSD)).isEmpty();
        final List<XmlElement> written = children(events, "Event");
        assertThat(users(written.get(0))).containsExactly("john|mail.com", "jack|gmail.com");
        // Each item's own Data: the second recipient has no status, so no Data.
        final List<XmlElement> destinationUsers = children(find(written.get(0), "EventDetail/Send/Destination"),
                "User");
        assertThat(having(destinationUsers.get(0), EVT, "Data", "Name", "status")).hasSize(1);
        assertThat(having(destinationUsers.get(1), EVT, "Data", "Name", "status")).isEmpty();
        // One object, not in an array, is one item.
        assertThat(users(written.get(1))).containsExactly("solo|x.org");
        // A loop, not a recursion.
        assertThat(result.xslt()).contains("<xsl:for-each select=").doesNotContain("<xsl:template name=\"recipient");
    }

    @Test
    void groupsNestAndAnInnerFieldReadsTheOuterItem() {
        // Each recipient a User, and each of its aliases a Data inside it holding the alias and the recipient's
        // domain: two levels, the inner reading the outer item by its name.
        final TranslationMapping mapping = sendMapping("json", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "recipients"}, "as": "recipient",
                 "fields": [{"path": "Id", "field": "id"},
                            {"path": "Data", "data_name": "alias", "for_each": {"split": "aliases", "on": ";"},
                             "as": "alias",
                             "fields": [{"path": "Data", "data_name": "name", "item": true},
                                        {"path": "Data", "data_name": "domain", "item": "recipient",
                                         "field": "domain"}]}]}"""));
        final XsltGenerator.Generated result = generate(mapping);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>\
                <string key="sender">jane</string>
                <array key="recipients">\
                <map><string key="id">john</string><string key="domain">mail.com</string>\
                <string key="aliases">js; jsmith ;</string></map>\
                <map><string key="id">jack</string><string key="domain">gmail.com</string></map>\
                </array></map></array>""");

        assertThat(invalid(events, XSD)).isEmpty();
        final List<XmlElement> recipients = children(find(children(events, "Event").getFirst(),
                "EventDetail/Send/Destination"), "User");
        assertThat(recipients).hasSize(2);
        final List<XmlElement> aliases = having(recipients.getFirst(), EVT, "Data", "Name", "alias");
        assertThat(aliases).extracting(alias -> having(alias, EVT, "Data", "Name", "name").getFirst().get("Value")
                                                + "@" + having(alias, EVT, "Data", "Name", "domain").getFirst()
                                                        .get("Value"))
                .containsExactly("js@mail.com", "jsmith@mail.com");
        assertThat(having(recipients.get(1), EVT, "Data", "Name", "alias")).isEmpty();
    }

    @Test
    void aDataGroupWritesAContainerPerItemHoldingItsData() {
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "first_recipient"}"""), entry("""
                {"path": "EventDetail/Send/Data", "data_name": "recipient",
                 "for_each": {"split": "recipients", "on": ","},
                 "fields": [{"path": "Data", "data_name": "id", "item": true, "transform": "strip_domain"},
                            {"path": "Data", "data_name": "domain", "item": true, "transform": "domain"}]}"""));
        final XsltGenerator.Generated result = generate(mapping);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <records xmlns="records:2">
                <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane"/>\
                <data name="first_recipient" value="john"/>\
                <data name="recipients" value="john@mail.com,,jack@gmail.com, "/></record>
                </records>""");

        assertThat(invalid(events, XSD)).isEmpty();
        final XmlElement send = find(children(events, "Event").getFirst(), "EventDetail/Send");
        assertThat(having(send, EVT, "Data", "Name", "recipient"))
                .extracting(recipient -> children(recipient, "Data").stream()
                        .map(data -> data.get("Name") + "=" + data.get("Value")).toList())
                .containsExactly(List.of("id=john", "domain=mail.com"), List.of("id=jack", "domain=gmail.com"));
    }

    @Test
    void zippedListsPairByPositionAndUnequalOnesWriteNoEvent() {
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "as": "pair",
                 "for_each": {"zip": {"address": "recipients", "status": "statuses"}, "on": ","},
                 "fields": [{"path": "Id", "field": "address"},
                            {"path": "Data", "data_name": "status", "field": "status"}]}"""));
        final XsltGenerator.Generated result = generate(mapping);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final String xslt = result.xslt().replace("</xsl:stylesheet>", LOGGING_STUB + "</xsl:stylesheet>");

        final XmlElement events = transform(xslt, """
                <records xmlns="records:2">
                <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane"/>\
                <data name="recipients" value="john,jack"/><data name="statuses" value="sent, bounced"/></record>
                <record><data name="time" value="2026-09-28T10:01:00.000Z"/><data name="sender" value="jane"/>\
                <data name="recipients" value="john,jack,jill"/><data name="statuses" value="sent,bounced"/></record>
                </records>""");

        final List<XmlElement> written = children(events, "Event");
        assertThat(written).hasSize(1);
        final List<XmlElement> recipients = children(find(written.getFirst(), "EventDetail/Send/Destination"), "User");
        assertThat(recipients).extracting(user -> text(user, "Id") + "="
                                                  + having(user, EVT, "Data", "Name", "status").getFirst().get("Value"))
                .containsExactly("john=sent", "jack=bounced");
        // The second record's lists differ: an error naming the lists' lengths and the record, and no event.
        final List<XmlElement> logged = events.iterElements().stream()
                .filter(element -> "logged".equals(element.localName()))
                .toList();
        assertThat(logged).singleElement().satisfies(line -> {
            assertThat(line.get("level")).isEqualTo("ERROR");
            assertThat(line.text()).contains("EventDetail/Send/Destination/User", "recipients 3", "statuses 2",
                    "the event is not written: record 7");
        });
    }

    @Test
    void aFieldOfAGroupReadsTheRecordWithScopeRecord() {
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ";"},
                 "fields": [{"path": "Id", "item": true},
                            {"path": "Domain", "field": "domain", "scope": "record"}]}"""));
        final XsltGenerator.Generated result = generate(mapping);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <records xmlns="records:2">
                <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane"/>\
                <data name="domain" value="CORP"/><data name="recipients" value="john;jack"/></record>
                </records>""");

        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(users(children(events, "Event").getFirst())).containsExactly("john|CORP", "jack|CORP");
    }

    @Test
    void xmlRepeatedChildrenAreAnArray() {
        final List<Object> fields = new ArrayList<>(List.of(
                entry("""
                        {"path": "EventDetail/TypeId", "value": "Send"}"""),
                entry("""
                        {"path": "EventDetail/Send/Source/User/Id", "field": "From"}"""),
                entry("""
                        {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "To/Address"},
                         "fields": [{"path": "Id", "field": "@user"}, {"path": "Domain", "field": "@host"}]}""")));
        final List<Map<String, Object>> common = json("""
                [{"path": "EventTime/TimeCreated", "field": "Time"},
                 {"path": "EventSource/System/Name", "value": "Mail"},
                 {"path": "EventSource/System/Environment", "value": "Dev"},
                 {"path": "EventSource/Generator", "value": "mta"},
                 {"path": "EventSource/Device/HostName", "value": "mx1"}]""");
        final XsltGenerator.Generated result = generate(mapping(with("input", "xml", "root", "Mails", "record",
                "Mail", "common", common, "events", List.of(rule("send", List.of(), fields)))));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();

        final XmlElement events = transform(result.xslt(), """
                <Mails><Mail><Time>2026-09-28T10:00:00.000Z</Time><From>jane</From>
                <To><Address user="john" host="mail.com"/>\
                <Address user="jack" host="gmail.com"/></To></Mail></Mails>""");

        assertThat(invalid(events, XSD)).isEmpty();
        assertThat(users(children(events, "Event").getFirst())).containsExactly("john|mail.com", "jack|gmail.com");
    }

    @Test
    void aVeryLongListIsSplitWithoutARegexOverIt() {
        // Thousands of recipients in a line of a few hundred KB: a literal split (tokenize with the q flag) and a
        // loop, nothing that recurses or backtracks over the whole value.
        final int count = 20_000;
        final StringBuilder list = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                list.append(", ");
            }
            list.append("user").append(i).append("@example").append(i % 7).append(".com");
        }
        assertThat(list.length()).isGreaterThan(300_000);
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "as": "recipient",
                 "fields": [{"path": "Id", "item": true, "transform": "strip_domain"},
                            {"path": "Domain", "item": true, "transform": "domain"}]}"""));
        final XsltGenerator.Generated result = generate(mapping);
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        assertThat(result.xslt()).contains("tokenize(string(.), ',', 'q')");

        final XmlElement events = transform(result.xslt(), "<records xmlns=\"records:2\"><record>"
                                                           + "<data name=\"time\" value=\"2026-09-28T10:00:00.000Z\"/>"
                                                           + "<data name=\"sender\" value=\"jane\"/>"
                                                           + "<data name=\"recipients\" value=\"" + list + "\"/>"
                                                           + "</record></records>");

        final List<String> written = users(children(events, "Event").getFirst());
        assertThat(written).hasSize(count);
        assertThat(written.getFirst()).isEqualTo("user0|example0.com");
        assertThat(written.getLast()).isEqualTo("user" + (count - 1) + "|example" + ((count - 1) % 7) + ".com");
    }

    @Test
    void aGroupsElementMustBeOneTheSchemaLetsRepeat() {
        final XsltGenerator.Generated result = generate(sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "recipient"}"""), entry("""
                {"path": "EventSource/Device", "for_each": {"split": "hosts", "on": ","},
                 "fields": [{"path": "HostName", "item": true}]}""")));
        assertThat(result.ok()).isFalse();
        assertThat(result.problems()).anySatisfy(problem -> assertThat(problem)
                .contains("[send] EventSource/Device", "the schema allows one Device there"));
    }

    @Test
    void nothingElseInTheRuleMapsBelowAGroup() {
        final XsltGenerator.Generated result = generate(sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Domain", "field": "domain"}"""), entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "fields": [{"path": "Id", "item": true}]}""")));
        assertThat(result.ok()).isFalse();
        assertThat(result.problems()).anySatisfy(problem -> assertThat(problem)
                .contains("[send] Event/EventDetail/Send/Destination/User is written once per item",
                        "map nothing else below it", "Event/EventDetail/Send/Destination/User/Domain"));
    }

    @Test
    void repeatStillWritesOneElementPerValue() {
        final XsltGenerator.Generated result = generate(sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "repeat": true,
                 "xpath": "tokenize(data[@name='recipients']/@value, ',')"}""")));
        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        final XmlElement events = transform(result.xslt(), """
                <records xmlns="records:2">
                <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane"/>\
                <data name="recipients" value="john,jack"/></record>
                </records>""");
        assertThat(users(children(events, "Event").getFirst())).containsExactly("john|null", "jack|null");
    }

    @Test
    void aMappingWithAGroupIsKeptAsGiven() {
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "as": "pair",
                 "for_each": {"zip": {"address": "recipients", "status": "statuses"}, "on": ","},
                 "fields": [{"path": "Id", "field": "address", "transform": "lower"},
                            {"path": "Data", "data_name": "sender", "field": "sender", "scope": "record"}]}"""));
        final Map<String, Object> kept = mapping.toJson(DumpMode.COMPACT);
        assertThat(TranslationMapping.fromJson(kept)).isEqualTo(mapping);
        assertThatThrownBy(() -> FieldMapping.fromJson(entry("""
                {"path": "Id", "item": true}"""))).hasMessageContaining("item reads a group's item");
    }

    @Test
    void aGroupsLoopReadBackIsNamedAsNotRead() {
        // rebuild_mapping doesn't read a group's loop back into a group entry: it says so rather than lose it.
        final XsltGenerator.Generated result = generate(sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "fields": [{"path": "Id", "item": true}]}""")));
        final RebuiltMapping rebuilt = XsltReader.rebuild(result.xslt(), null, null);
        assertThat(rebuilt.mapping() == null
                   || rebuilt.mapping().everyEntry().stream().noneMatch(FieldMapping::isGroup)).isTrue();
        assertThat(String.join(" ", rebuilt.lost()) + String.join(" ", rebuilt.raw())
                   + String.join(" ", rebuilt.notes()) + String.join(" ", rebuilt.problems()))
                .as(String.valueOf(rebuilt.summary())).contains("for-each");
    }
}
