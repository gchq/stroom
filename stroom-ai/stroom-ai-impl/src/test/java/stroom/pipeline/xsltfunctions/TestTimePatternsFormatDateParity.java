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

package stroom.pipeline.xsltfunctions;

import stroom.ai.impl.tools.generation.XPathText;
import stroom.ai.impl.tools.sample.TimePatterns;
import stroom.util.date.DateUtil;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// The tools' check of a time_format against a sample's values ([TimePatterns#checkTimeFormat], null when the
/// format reads every value) against Stroom's own `stroom:format-date` ([FormatDate#parseDate]) on the same values,
/// passed as the generated XSLT passes them: trimmed ([XPathText#trimmedText(String)]). A value the check passes is
/// one Stroom reads to the right time; one Stroom can't is refused (found in the audit of 2026-11-26).
class TestTimePatternsFormatDateParity {

    @Test
    void aValueTheCheckSaysTheFormatReadsIsReadByStroomToTheSameTime() {
        final List<Case> cases = List.of(
                // A 12-hour clock without am/pm.
                new Case("yyyy-MM-dd hh:mm:ss", "2026-11-26 03:15:00", "2026-11-26T03:15:00Z"),
                // White space round the value: the check and the XSLT both trim it.
                new Case("yyyy-MM-dd'T'HH:mm:ssXXX", "2026-11-26T10:00:00Z ", "2026-11-26T10:00:00Z"),
                new Case("yyyy-MM-dd HH:mm:ss", " 2026-11-26 10:00:00", "2026-11-26T10:00:00Z"),
                // A month or an hour out of range: Stroom gives none, so the check refuses it.
                new Case("yyyy-MM-dd HH:mm:ss", "2026-13-01 10:00:00", null),
                new Case("yyyy-MM-dd HH:mm:ss", "2026-11-26 25:00:00", null),
                // A date alone, in a pattern with a time.
                new Case("yyyy-MM-dd", "2026-11-26", "2026-11-26T00:00:00Z"));
        final List<String> out = new ArrayList<>();
        for (final Case c : cases) {
            final String check = TimePatterns.checkTimeFormat(c.pattern(), List.of(c.value()));
            final String stroom = stroom(c.pattern(), c.value());
            final boolean checkReads = check == null;
            final boolean stroomRight = stroom != null && (c.expected() == null || c.expected().equals(stroom));
            if (checkReads != stroomRight) {
                out.add("'" + c.value() + "' by '" + c.pattern() + "': the check " + (checkReads
                        ? "says it reads"
                        : "refuses: " + check) + "; Stroom gives " + stroom);
            }
        }
        assertThat(out).isEmpty();
    }

    @Test
    void anEpochMillisecondsValueTheCheckAcceptsIsReadByStroom() {
        // The XSLT passes epoch_ms, trimmed and read as a number, to the one-argument format-date (Long.parseLong).
        final String value = " 1764151200000";
        assertThat(TimePatterns.checkTimeFormat("epoch_ms", List.of(value))).as("the check").isNull();
        final String expression = XPathText.timeExpression("epoch_ms", null, "$v");
        final String argument = expression.substring("stroom:format-date(".length(), expression.length() - 1);
        String written;
        try {
            // FormatDate.convertMilliseconds: Long.parseLong of the argument's text, then Stroom's normal format.
            written = DateUtil.createNormalDateTimeString(Long.parseLong(xpath(argument, value)));
        } catch (final RuntimeException e) {
            written = null;
        }
        assertThat(written).as("Stroom's format-date of '%s'", value).isNotNull();
    }

    // What Stroom's format-date makes of the value (UTC), or null when it can't parse it.
    private static String stroom(final String pattern, final String value) {
        final FormatDate formatDate = new FormatDate(null);
        try {
            return Instant.ofEpochMilli(formatDate.parseDate(null, xpath(XPathText.trimmedText("$v"), value),
                    pattern, "UTC")).toString();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    // An XPath expression of $v run by Saxon, as the generated XSLT runs it.
    private static String xpath(final String expression, final String value) {
        try {
            final XPathCompiler compiler = new Processor(false).newXPathCompiler();
            compiler.declareVariable(new QName("v"));
            final XPathSelector selector = compiler.compile(expression).load();
            selector.setVariable(new QName("v"), new XdmAtomicValue(value));
            return selector.evaluateSingle().getStringValue();
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }


    // --------------------------------------------------------------------------------


    private record Case(String pattern, String value, String expected) {

    }
}
