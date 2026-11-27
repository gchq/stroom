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

import static org.assertj.core.api.Assertions.assertThat;

/// The fields of the records an xpath reads, as the records spell them.
class TestXPathFieldReads {

    @Test
    void aJsonRecordsKeysAreItsDottedFields() {
        assertThat(XPathFieldReads.fields("*[@key='event']/*[@key='app']")).containsExactly("event.app");
        assertThat(XPathFieldReads.plainField("string((*[@key='event']/*[@key='app'])[1])")).isEqualTo("event.app");
        // A flat key holding a dot is bracketed, as the records spell it.
        assertThat(XPathFieldReads.plainField("*[@key='log.level']")).isEqualTo("['log.level']");
        assertThat(XPathFieldReads.plainField("*[@key='labels']/*[@key='app.kubernetes.io/name']"))
                .isEqualTo("labels['app.kubernetes.io/name']");
    }

    @Test
    void theKeysOfJsonHeldInAFieldAndInAKeyOfItAreFieldsBelowIt() {
        // Seen in testing: event.original JSON text, its details JSON text again, query_text placed by an xpath.
        final String xpath = "json-to-xml(json-to-xml(*[@key='event']/*[@key='original'])/*/*[@key='details'])"
                             + "/*/*[@key='query_text']";
        assertThat(XPathFieldReads.fields(xpath)).containsExactly("event.original.details.query_text");
        assertThat(XPathFieldReads.plainField(xpath)).isEqualTo("event.original.details.query_text");
        assertThat(XPathFieldReads.plainField("mcp:json-to-xml(string(*[@key='msg']))/*/*[@key='user'][1]"))
                .isEqualTo("msg.user");
        // The held JSON's top is the held field read, not a value as it is.
        assertThat(XPathFieldReads.fields("json-to-xml(*[@key='msg'])/*")).containsExactly("msg");
        assertThat(XPathFieldReads.plainField("json-to-xml(*[@key='msg'])/*")).isNull();
    }

    @Test
    void dataSplitterAndXmlRecordsAreReadByTheirNamesAndPaths() {
        assertThat(XPathFieldReads.plainField("data[@name='user']/@value")).isEqualTo("user");
        assertThat(XPathFieldReads.plainField("data[@name='a']/data[@name='b']/@value")).isEqualTo("a/b");
        assertThat(XPathFieldReads.plainField("System/EventID")).isEqualTo("System/EventID");
        assertThat(XPathFieldReads.plainField("System/Provider/@Name")).isEqualTo("System/Provider/@Name");
        assertThat(XPathFieldReads.plainField("EventData/Data[@Name='TargetUserName']"))
                .isEqualTo("EventData/Data[@Name='TargetUserName']");
    }

    @Test
    void everyFieldAnExpressionReadsIsListedButOnlyAPathIsAPlainRead() {
        final String xpath = "concat(*[@key='first'], ' ', *[@key='last'])";
        assertThat(XPathFieldReads.fields(xpath)).containsExactly("first", "last");
        assertThat(XPathFieldReads.plainField(xpath)).isNull();
        assertThat(XPathFieldReads.plainField("substring(*[@key='a'], 2)")).isNull();
        // A field's items: read, but not as it is.
        assertThat(XPathFieldReads.fields("*[@key='tags']/*[1]")).containsExactly("tags");
        assertThat(XPathFieldReads.plainField("*[@key='tags']/*[1]")).isNull();
        // What a predicate tests isn't written.
        assertThat(XPathFieldReads.fields("*[@key='a'][../*[@key='b'] = 'x']")).containsExactly("a");
    }

    @Test
    void aFieldTrimmedOrFilteredIsReadButNotAsItIs() {
        // normalize-space() changes the value (trimmed, white space folded), and a filter may give none of it: a
        // Data copy of the field would differ, so neither is the field as it is.
        assertThat(XPathFieldReads.fields("normalize-space(*[@key='a'])")).containsExactly("a");
        assertThat(XPathFieldReads.plainField("normalize-space(*[@key='a'])")).isNull();
        assertThat(XPathFieldReads.plainField("normalize-space(data[@name='a']/@value)")).isNull();
        assertThat(XPathFieldReads.fields("*[@key='a'][. != '']")).containsExactly("a");
        assertThat(XPathFieldReads.plainField("*[@key='a'][. != '']")).isNull();
        assertThat(XPathFieldReads.plainField("*[@key='a'][. != '']/*[@key='b']")).isNull();
        assertThat(XPathFieldReads.plainField("string(*[@key='a'][2])")).isNull();
        // Its first value, and string() of it, are as it is.
        assertThat(XPathFieldReads.plainField("string(*[@key='a'][1])")).isEqualTo("a");
        assertThat(XPathFieldReads.plainField("string(*[@key='a'])")).isEqualTo("a");
    }

    @Test
    void whatIsNotAPathTheRecordsNameReadsNothing() {
        assertThat(XPathFieldReads.fields(null)).isEmpty();
        assertThat(XPathFieldReads.fields("")).isEmpty();
        assertThat(XPathFieldReads.fields("'constant'")).isEmpty();
        assertThat(XPathFieldReads.fields("$other/*[@key='a']")).isEmpty();
        assertThat(XPathFieldReads.fields("/map/*[@key='a']")).isEmpty();
        assertThat(XPathFieldReads.fields("nope:f(*[@key='a'"))
                .as("doesn't parse").isEmpty();
        // Mixed shapes name no field.
        assertThat(XPathFieldReads.plainField("*[@key='a']/data[@name='b']")).isNull();
        assertThat(XPathFieldReads.plainField("$record/*[@key='a']")).isEqualTo("a");
    }
}
