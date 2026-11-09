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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.generation.XPathRegex;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Syslog headers inside the text they carry: a relay's before the source's, a collector's words before one, and
/// what follows the host in an RFC 5424 header.
class TestSyslogHeaders {

    private static final String SOURCE = "<38>Nov  9 09:59:58 bastion01 sshd[2211]: Accepted password for alice";
    private static final String RELAYED = "<13>Nov  9 10:00:01 relay01 " + SOURCE;
    // After a shipper read the header up to the host: a quoted value holding escaped
    // brackets, a second element, and a byte order mark before the message.
    private static final String TAIL = "Connection - - [synolog@6574 event_id=\"0x0004\" synotype=\"Connection\" "
                                       + "luser=\"EXAMPLE\\\\alice\" event=\"User [EXAMPLE\\\\alice\\] from "
                                       + "[192.0.2.10\\] accessed shared folder [Projects\\].\"][meta "
                                       + "sequenceId=\"12\"] \uFEFFUser [EXAMPLE\\alice] accessed shared folder "
                                       + "[Projects].";

    @Test
    void aRelaysHeaderIsTakenOffToReachTheSourcesLine() {
        assertThat(SyslogHeaders.innermost(RELAYED)).isEqualTo(SOURCE);
        assertThat(SyslogHeaders.innermost("<13>1 2026-11-09T10:00:01Z relay01 rsyslogd - - - " + SOURCE))
                .isEqualTo(SOURCE);
        // Two relays.
        assertThat(SyslogHeaders.innermost("<13>Nov  9 10:00:02 relay02 " + RELAYED)).isEqualTo(SOURCE);
        // Nothing wraps a plain line.
        assertThat(SyslogHeaders.innermost(SOURCE)).isEqualTo(SOURCE);
        assertThat(SyslogHeaders.innermost("plain text")).isEqualTo("plain text");
    }

    @Test
    void aCollectorsWordsBeforeAHeaderAreCounted() {
        assertThat(SyslogHeaders.prefixWords("2026-11-09T10:00:01Z relay01 " + SOURCE)).isEqualTo(2);
        assertThat(SyslogHeaders.prefixWords("relay01 " + SOURCE)).isEqualTo(1);
        assertThat(SyslogHeaders.innermost("2026-11-09T10:00:01Z relay01 " + RELAYED)).isEqualTo(SOURCE);
        // A line that is a header has none; nor one with no header after a few words.
        assertThat(SyslogHeaders.prefixWords(SOURCE)).isZero();
        assertThat(SyslogHeaders.prefixWords("one two three four five " + SOURCE)).isZero();
        assertThat(SyslogHeaders.prefixWords("2026-11-09T10:00:01Z INFO started")).isZero();
        assertThat(SyslogHeaders.withoutWords("a  b " + SOURCE, 2)).isEqualTo(SOURCE);
    }

    @Test
    void theEndOfAnRfc5424HeaderAfterTheHostIsRead() {
        assertThat(SyslogHeaders.isTail(TAIL)).isTrue();
        assertThat(SyslogHeaders.hasStructuredData(TAIL)).isTrue();
        final Matcher m = SyslogHeaders.tailOf(TAIL);
        assertThat(m.group(1)).isEqualTo("Connection");
        // Every element, to its own closing bracket: an escaped one inside a value doesn't end it.
        assertThat(m.group(4)).endsWith("[meta sequenceId=\"12\"]");
        assertThat(m.group(5)).isEqualTo("User [EXAMPLE\\alice] accessed shared folder [Projects].");
        // No structured data: a process id that is a number.
        assertThat(SyslogHeaders.isTail("dovecot 11886 - - Indexed 3 messages")).isTrue();
        assertThat(SyslogHeaders.hasStructuredData("dovecot 11886 - - Indexed 3 messages")).isFalse();
    }

    @Test
    void textThatOnlyLooksLikeATailIsNot() {
        assertThat(SyslogHeaders.isTail("User alice logged - in")).isFalse();
        assertThat(SyslogHeaders.isTail("a b c [not structured] rest")).isFalse();
        assertThat(SyslogHeaders.isTail("")).isFalse();
        assertThat(SyslogHeaders.isTail(null)).isFalse();
    }

    @Test
    void theRegexesRunInXPathsDialectToo() {
        // The draft's extractions run them in the XSLT's analyze-string().
        assertThat(XPathRegex.problem(SyslogHeaders.TAIL_5424)).isEmpty();
        assertThat(XPathRegex.problem(SplitterDryRun.SYSLOG_5424)).isEmpty();
        assertThat(XPathRegex.problem(SplitterDryRun.SYSLOG_3164)).isEmpty();
    }

    @Test
    void longStructuredDataDoesNotOverflowTheStack() {
        // Seen with other regexes: a repetition of one character at a time recursed past the stack's depth.
        final String value = "x".repeat(200_000);
        final String line = "<14>1 2026-11-09T10:00:00Z host app - - [id@1 a=\"" + value + "\" b=\"\\]\"] message";
        final Matcher m = Pattern.compile(SplitterDryRun.SYSLOG_5424).matcher(line);

        assertThat(m.matches()).isTrue();
        assertThat(m.group(8)).isEqualTo("message");
    }
}
