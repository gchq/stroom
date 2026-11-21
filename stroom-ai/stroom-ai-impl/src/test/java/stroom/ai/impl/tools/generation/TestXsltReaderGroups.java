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

import stroom.ai.impl.tools.validation.XsltVersionHistory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Reading a group's loop back (`rebuild_mapping`): each item source (split, array, zip), nested groups, Data groups,
/// fields reading the record or an outer item, the mapping lost or kept; and loops the generator doesn't write, said
/// as not read.
class TestXsltReaderGroups extends XsltRuns {

    private static final String SPLIT_RECORDS = """
            <records xmlns="records:2">
            <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane"/>\
            <data name="domain" value="CORP"/><data name="first_recipient" value="john"/>\
            <data name="recipients" value="john@mail.com,,jack@gmail.com, "/></record>
            <record><data name="time" value="2026-09-28T10:01:00.000Z"/><data name="sender" value="jill"/></record>
            </records>""";
    private static final String ZIP_RECORDS = """
            <records xmlns="records:2">
            <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane"/>\
            <data name="recipients" value="john,jack"/><data name="statuses" value="sent, bounced"/></record>
            <record><data name="time" value="2026-09-28T10:01:00.000Z"/><data name="sender" value="jane"/>\
            <data name="recipients" value="john,jack,jill"/><data name="statuses" value="sent,bounced"/></record>
            </records>""";
    private static final String JSON_RECORDS = """
            <array xmlns="http://www.w3.org/2013/XSL/json"><map>
            <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>\
            <string key="sender">jane</string>
            <array key="recipients">\
            <map><string key="id">john</string><map key="address"><string key="domain">mail.com</string></map>\
            <string key="domain">mail.com</string><string key="aliases">js; jsmith ;</string></map>\
            <map><string key="id">jack</string><string key="domain">gmail.com</string></map>\
            </array></map><map>
            <string key="time">2026-09-28T10:01:00.000Z</string><string key="host">h</string>\
            <string key="sender">jane</string>
            <map key="recipients"><string key="id">solo</string></map></map></array>""";

    @Test
    void aSplitGroupIsReadBackAsTheGroupThatWroteIt() {
        // A Data group of each recipient, its Data the piece itself transformed; a User group of each, one field
        // reading the record.
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "as": "recipient",
                 "fields": [{"path": "Id", "item": true, "transform": "strip_domain"},
                            {"path": "Domain", "field": "domain", "scope": "record"}]}"""), entry("""
                {"path": "EventDetail/Send/Data", "data_name": "recipient",
                 "for_each": {"split": "recipients", "on": ","},
                 "fields": [{"path": "Data", "data_name": "id", "item": true, "transform": "strip_domain"},
                            {"path": "Data", "data_name": "domain", "item": true, "transform": "domain"}]}"""));

        assertRoundTrip(mapping, SPLIT_RECORDS);
    }

    @Test
    void anArrayGroupWithAGroupInsideItIsReadBackNestedAsWritten() {
        // Each recipient object a User (a key of it, and a key of an object inside it, transformed), and each of its
        // aliases (a split of a key of the recipient) a Data group inside it, reading the alias and the recipient.
        final TranslationMapping mapping = sendMapping("json", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "recipients"}, "as": "recipient",
                 "fields": [{"path": "Id", "field": "id"},
                            {"path": "Domain", "field": "address.domain", "transform": "upper"},
                            {"path": "Data", "data_name": "alias", "for_each": {"split": "aliases", "on": ";"},
                             "as": "alias",
                             "fields": [{"path": "Data", "data_name": "name", "item": true},
                                        {"path": "Data", "data_name": "domain", "item": "recipient",
                                         "field": "domain"},
                                        {"path": "Data", "data_name": "sender", "field": "sender",
                                         "scope": "record"}]}]}"""));

        assertRoundTrip(mapping, JSON_RECORDS);
    }

    @Test
    void aZipGroupIsReadBackWithItsListsByName() {
        // Lists paired by position, each read by its name, and a field of the record; the zip's check of the lists'
        // lengths stands before the Event.
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "as": "pair",
                 "for_each": {"zip": {"address": "recipients", "status": "statuses"}, "on": ","},
                 "fields": [{"path": "Id", "field": "address", "transform": "lower"},
                            {"path": "Data", "data_name": "status", "field": "status"},
                            {"path": "Data", "data_name": "sender", "field": "sender", "scope": "record"}]}"""));

        assertRoundTrip(mapping, ZIP_RECORDS);
    }

    @Test
    void anXmlArrayGroupIsReadBackWithItsKeys() {
        final List<Object> fields = List.of(
                entry("""
                        {"path": "EventDetail/TypeId", "value": "Send"}"""),
                entry("""
                        {"path": "EventDetail/Send/Source/User/Id", "field": "From"}"""),
                entry("""
                        {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "To/Address"},
                         "fields": [{"path": "Id", "field": "@user"},
                                    {"path": "Domain", "field": "@host", "transform": "lower"}]}"""));
        final List<Map<String, Object>> common = json("""
                [{"path": "EventTime/TimeCreated", "field": "Time"},
                 {"path": "EventSource/System/Name", "value": "Mail"},
                 {"path": "EventSource/System/Environment", "value": "Dev"},
                 {"path": "EventSource/Generator", "value": "mta"},
                 {"path": "EventSource/Device/HostName", "value": "mx1"}]""");
        final TranslationMapping mapping = mapping(with("input", "xml", "root", "Mails", "record", "Mail",
                "common", common, "events", List.of(rule("send", List.of(), fields))));

        assertRoundTrip(mapping, """
                <Mails><Mail><Time>2026-09-28T10:00:00.000Z</Time><From>jane</From>
                <To><Address user="john" host="Mail.com"/><Address user="jack" host="GMail.com"/></To></Mail>\
                </Mails>""");
    }

    @Test
    void groupsNestedAsDeepAsAMappingMayAreReadBackToTheLast() {
        // MAX_DEPTH groups, each a Data group of the objects of a key of the item around it.
        Map<String, Object> inner = entry("""
                {"path": "Data", "data_name": "leaf", "field": "name"}""");
        for (int depth = FieldGroup.MAX_DEPTH; depth >= 1; depth--) {
            final Map<String, Object> group = entry("""
                    {"path": "Data", "for_each": {"array": "kids"}}""");
            // Item names the generator's variable naming keeps as they are (level1 would be level_1).
            final String name = "level" + (char) ('a' + depth - 1);
            group.put("data_name", name);
            group.put("as", name);
            group.put("fields", List.of(entry("""
                    {"path": "Data", "data_name": "name", "field": "name"}"""), inner));
            inner = group;
        }
        inner.put("path", "EventDetail/Send/Data");
        inner.put("for_each", entry("""
                {"array": "items"}"""));
        final TranslationMapping mapping = sendMapping("json", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "sender"}"""), inner);
        final StringBuilder records = new StringBuilder("<array xmlns=\"http://www.w3.org/2013/XSL/json\"><map>"
                                                        + "<string key=\"time\">t</string><string key=\"host\">h"
                                                        + "</string><string key=\"sender\">jane</string>"
                                                        + "<array key=\"items\">");
        final StringBuilder close = new StringBuilder();
        for (int depth = 1; depth <= FieldGroup.MAX_DEPTH; depth++) {
            records.append("<map><string key=\"name\">n").append(depth).append("</string><array key=\"kids\">");
            close.insert(0, "</array></map>");
        }
        records.append(close).append("</array></map></array>");

        final RebuiltMapping lost = assertRoundTrip(mapping, records.toString());
        assertThat(groupDepth(lost.mapping().events().getFirst().fields())).isEqualTo(FieldGroup.MAX_DEPTH);
    }

    @Test
    void aHandEditInsideAGroupIsReadIntoTheGroup() {
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "as": "recipient", "fields": [{"path": "Id", "item": true, "transform": "strip_domain"}]}"""));
        final String code = codeOf(mapping);
        final String strip = "replace(replace(($recipient)[normalize-space(.)][1], '^[^\\\\]*\\\\', ''), '@.*$', '')";
        assertThat(code).contains(strip);
        // The Id made upper case by hand.
        final String edited = code.replace(strip, "upper-case(($recipient)[normalize-space(.)][1])");

        final RebuiltMapping rebuilt = XsltReader.rebuild(edited, mapping, code);

        assertThat(rebuilt.problems()).as("%s", rebuilt.summary()).isEmpty();
        assertThat(rebuilt.added()).containsExactly("rule send: EventDetail/Send/Destination/User");
        assertThat(group(rebuilt.mapping()).group().fields()).singleElement()
                .satisfies(field -> {
                    assertThat(field.transform()).isEqualTo("upper");
                    assertThat(field.item()).isEmpty();
                    assertThat(field.field()).isNull();
                });
        assertThat(normalise(codeOf(rebuilt.mapping()))).isEqualTo(normalise(edited));
    }

    @Test
    void aHandWrittenLoopIsSaidNotToBeReadAndNoGroupIsMadeOfIt() {
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "first_recipient"}"""));
        final String code = codeOf(mapping);
        // A loop written by hand: no variable holding its item, as no generated loop of a group is.
        final String loop = "<xsl:for-each select=\"tokenize(data[@name='recipients']/@value, ',')\"><Data "
                            + "Name=\"recipient\" Value=\"{.}\"/></xsl:for-each>";
        final String edited = once(code, "</Send>", loop + "</Send>");
        assertThat(edited).isNotEqualTo(code);

        // As an earlier generator wrote repeat there: kept so, it no longer generates.
        final TranslationMapping earlier = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "first_recipient"}"""), entry("""
                {"path": "EventDetail/Send/Payload/Email/From/EmailAddress", "repeat": true,
                 "xpath": "tokenize(data[@name='recipients']/@value, ',')"}"""));
        final XsltGenerator.Generated refused = XsltGenerator.generate(earlier, SCHEMA, "4.1.0");
        assertThat(refused.ok()).isFalse();

        final RebuiltMapping lost = XsltReader.rebuild(edited, null, null);
        final RebuiltMapping kept = XsltReader.rebuild(edited, mapping, code);
        final RebuiltMapping keptEarlier = XsltReader.rebuild(edited, earlier, refused.xslt());

        for (final RebuiltMapping rebuilt : List.of(lost, kept, keptEarlier)) {
            assertThat(rebuilt.problems()).anySatisfy(problem -> assertThat(problem)
                    .contains("EventDetail/Send/Data repeats (xsl:for-each)", "not other loops (one written by hand",
                            "for_each, as and fields"));
        }
    }

    @Test
    void aHandWrittenLoopOfAnElementAboveTheValueIsReadAsAGroupOnThatElement() {
        // One Email per address, each with its From/EmailAddress: Email repeats in Payload, From doesn't in Email, so
        // repeat on the EmailAddress is refused (a for_each on the Email suggested); the loop is that group.
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "first_recipient"}"""));
        final String code = codeOf(mapping);
        final String loop = "<Payload><xsl:for-each select=\"tokenize(data[@name='recipients']/@value, ',')\">"
                            + "<Email><From><EmailAddress><xsl:value-of select=\".\"/></EmailAddress></From>"
                            + "</Email></xsl:for-each></Payload>";
        final String edited = once(code, "</Send>", loop + "</Send>");
        assertThat(edited).isNotEqualTo(code);
        final String records = """
                <records xmlns="records:2">
                <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane"/>\
                <data name="first_recipient" value="john"/><data name="recipients" value="a@x.org,b@y.org"/></record>
                <record><data name="time" value="2026-09-28T10:01:00.000Z"/><data name="sender" value="jill"/>\
                <data name="first_recipient" value="jack"/><data name="recipients" value="c@z.org"/></record>
                </records>""";
        assertThat(invalid(transform(edited, records), XSD)).isEmpty();

        // As an earlier generator wrote repeat there: kept so, it no longer generates.
        final TranslationMapping earlier = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "first_recipient"}"""), entry("""
                {"path": "EventDetail/Send/Payload/Email/From/EmailAddress", "repeat": true,
                 "xpath": "tokenize(data[@name='recipients']/@value, ',')"}"""));
        final XsltGenerator.Generated refused = XsltGenerator.generate(earlier, SCHEMA, "4.1.0");
        assertThat(refused.ok()).isFalse();

        final RebuiltMapping lost = XsltReader.rebuild(edited, null, null);
        final RebuiltMapping kept = XsltReader.rebuild(edited, mapping, code);
        final RebuiltMapping keptEarlier = XsltReader.rebuild(edited, earlier, refused.xslt());

        for (final RebuiltMapping rebuilt : List.of(lost, kept, keptEarlier)) {
            assertThat(rebuilt.problems()).as("%s", rebuilt.summary()).isEmpty();
            assertThat(rebuilt.notes()).anySatisfy(note -> assertThat(note)
                    .contains("EventDetail/Send/Payload/Email", "a loop written by hand", "for_each group"));
            final FieldMapping group = group(rebuilt.mapping());
            assertThat(group.toJson(DumpMode.COMPACT)).isEqualTo(entry("""
                    {"path": "EventDetail/Send/Payload/Email", "for_each": {"split": "recipients", "on": ","},
                     "fields": [{"path": "From/EmailAddress", "item": true}]}"""));
            assertThat(rebuilt.mapping().everyEntry()).noneMatch(FieldMapping::repeat);
            final String regenerated = codeOf(rebuilt.mapping());
            assertThat(invalid(transform(regenerated, records), XSD)).isEmpty();
            assertThat(events(regenerated, records)).isEqualTo(events(edited, records));
        }
    }

    @Test
    void aRepeatOfTheElementHoldingALeafIsReadBackLostAsAGroupWritingTheSameEvents() {
        // repeat on Destination/User/Id writes a User a value: its loop, read with the mapping lost, is a group on
        // the User (no variable holding its item, so read as a loop written by hand); kept, the entry is kept.
        final List<TranslationMapping> mappings = List.of(sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "rcpt", "repeat": true}""")),
                sendMapping("json", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "rcpt", "repeat": true}""")));
        final List<String> records = List.of("""
                <records xmlns="records:2">
                <record><data name="time" value="2026-09-28T10:00:00.000Z"/><data name="sender" value="jane"/>\
                <data name="rcpt" value="john"/><data name="rcpt" value="jack"/></record>
                </records>""", """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map>
                <string key="time">2026-09-28T10:00:00.000Z</string><string key="host">h</string>\
                <string key="sender">jane</string>
                <array key="rcpt"><string>john</string><string>jack</string></array></map></array>""");
        for (int n = 0; n < mappings.size(); n++) {
            final TranslationMapping mapping = mappings.get(n);
            final String code = codeOf(mapping);

            final RebuiltMapping lost = XsltReader.rebuild(code, null, null);
            final RebuiltMapping kept = XsltReader.rebuild(code, mapping, code);

            assertThat(lost.problems()).as("%s", lost.summary()).isEmpty();
            assertThat(group(lost.mapping()).toJson(DumpMode.COMPACT)).isEqualTo(entry("""
                    {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "rcpt"},
                     "fields": [{"path": "Id", "item": true}]}"""));
            final String regenerated = codeOf(lost.mapping());
            assertThat(events(regenerated, records.get(n))).isEqualTo(events(code, records.get(n)))
                    .contains("john", "jack");
            assertThat(invalid(transform(regenerated, records.get(n)), XSD)).isEmpty();
            assertThat(kept.problems()).as("%s", kept.summary()).isEmpty();
            assertThat(kept.added()).isEmpty();
            assertThat(normalise(codeOf(kept.mapping()))).isEqualTo(normalise(code));
        }
    }

    @Test
    void aHandWrittenLoopReadingBelowItsValueIsSaidNotToBeRead() {
        // The loop's value read as a node (its attribute): not a value an item is, so not read as a group.
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User/Id", "field": "first_recipient"}"""));
        final String code = codeOf(mapping);
        final String loop = "<Payload><xsl:for-each select=\"data[@name='recipients']\">"
                            + "<Email><From><EmailAddress><xsl:value-of select=\"@value\"/></EmailAddress></From>"
                            + "</Email></xsl:for-each></Payload>";
        final String edited = once(code, "</Send>", loop + "</Send>");

        final RebuiltMapping rebuilt = XsltReader.rebuild(edited, null, null);

        assertThat(rebuilt.problems()).anySatisfy(problem -> assertThat(problem)
                .contains("EventDetail/Send/Payload/Email repeats (xsl:for-each)", "not other loops"));
        assertThat(rebuilt.mapping().everyEntry()).noneMatch(FieldMapping::isGroup);
    }

    @Test
    void aGroupsLoopWhoseItemsAreNotTheGeneratorsIsSaidNotToBeRead() {
        final TranslationMapping mapping = sendMapping("data_splitter", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "as": "recipient", "fields": [{"path": "Id", "item": true}]}"""));
        final String code = codeOf(mapping);
        final String items = "((data[@name='recipients']/@value) ! tokenize(string(.), ',', 'q') ! "
                             + "replace(., '^\\s+|\\s+$', ''))[normalize-space(.)]";
        assertThat(code).contains("<xsl:for-each select=\"" + items + "\">");
        // Made distinct by hand: no group's items are.
        final String edited = code.replace("<xsl:for-each select=\"" + items + "\">",
                "<xsl:for-each select=\"distinct-values(" + items + ")\">");

        final RebuiltMapping rebuilt = XsltReader.rebuild(edited, null, null);

        assertThat(rebuilt.problems()).anySatisfy(problem -> assertThat(problem)
                .contains("EventDetail/Send/Destination/User", "aren't a split, an array or a zip", "not read"));
        assertThat(rebuilt.mapping() == null || rebuilt.mapping().everyEntry().stream()
                .noneMatch(FieldMapping::isGroup)).isTrue();
    }

    @Test
    void aFieldReadingTwoItemsInOneValueIsSaidNotToBeRead() {
        final TranslationMapping mapping = sendMapping("json", entry("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "recipients"}, "as": "recipient",
                 "fields": [{"path": "Data", "data_name": "alias", "for_each": {"split": "aliases", "on": ";"},
                             "as": "alias", "fields": [{"path": "Data", "data_name": "name", "item": true}]}]}"""));
        final String code = codeOf(mapping);
        final String name = "{($alias)[normalize-space(.)][1]}";
        assertThat(code).contains(name);
        // The alias and the recipient's id in one value, by hand.
        final String edited = code.replace(name, "{concat($alias, '@', $recipient/*[@key='id'])}");

        final RebuiltMapping rebuilt = XsltReader.rebuild(edited, null, null);

        assertThat(rebuilt.problems()).anySatisfy(problem -> assertThat(problem)
                .contains("Data alias > Data Data name", "reads its item in an expression no group field writes",
                        "not read"));
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    // Generated, read back lost and kept: lost, the groups read back are the mapping's and write the same events;
    // kept, every entry is kept and the XSLT regenerates as it was.
    private static RebuiltMapping assertRoundTrip(final TranslationMapping mapping, final String records) {
        final String code = codeOf(mapping);

        final RebuiltMapping lost = XsltReader.rebuild(code, null, null);
        assertThat(lost.problems()).as("%s", lost.summary()).isEmpty();
        // Every field of a group read as an idiom (an XML input's other fields are kept as xpath, as they always
        // are).
        final List<String> grouped = groups(mapping).stream().map(FieldMapping::path).toList();
        assertThat(lost.raw()).as("%s", lost.summary())
                .noneMatch(raw -> grouped.stream().anyMatch(raw::contains));
        assertThat(groups(lost.mapping())).isEqualTo(groups(mapping));
        assertThat(events(codeOf(lost.mapping()), records)).isEqualTo(events(code, records));

        final RebuiltMapping kept = XsltReader.rebuild(code, mapping, code);
        assertThat(kept.problems()).as("%s", kept.summary()).isEmpty();
        assertThat(kept.added()).isEmpty();
        assertThat(kept.removed()).isEmpty();
        assertThat(normalise(codeOf(kept.mapping()))).isEqualTo(normalise(code));
        return lost;
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

    private static Map<String, Object> entry(final String text) {
        return json(text);
    }

    private static List<FieldMapping> groups(final TranslationMapping mapping) {
        return mapping.everyEntry().stream().filter(FieldMapping::isGroup).toList();
    }

    private static FieldMapping group(final TranslationMapping mapping) {
        return groups(mapping).getFirst();
    }

    // How deep the groups among these entries nest.
    private static int groupDepth(final List<FieldMapping> entries) {
        int deepest = 0;
        for (final FieldMapping entry : entries) {
            if (entry.isGroup()) {
                deepest = Math.max(deepest, 1 + groupDepth(entry.group().fields()));
            }
        }
        return deepest;
    }

    private static String codeOf(final TranslationMapping mapping) {
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
        return result.xslt();
    }

    private static String events(final String code, final String records) {
        return XmlSerialiser.toXml(transform(code, records));
    }

    // The XSLT without what isn't an edit (the declaration, space between tags).
    private static String normalise(final String text) {
        final String stripped = XsltVersionHistory.strip(text).replaceFirst("^\\s*<\\?xml[^>]*\\?>", "");
        return stripped.replaceAll(">\\s+<", "><").strip();
    }

    private static String once(final String text, final String old, final String replacement) {
        final int at = text.indexOf(old);
        return at < 0
                ? text
                : text.substring(0, at) + replacement + text.substring(at + old.length());
    }
}
