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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Whether held XML is structured data or a document (decided by the user 2026-11-22): a sibling naming its
/// format, HTML's vocabulary, then how alike its structure is from record to record.
class TestHeldMarkup {

    private static final String FIELD = "msg.body";

    private static List<Map<String, Object>> records(final List<String> bodies, final String type) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final String body : bodies) {
            final Map<String, Object> record = new LinkedHashMap<>();
            if (type != null) {
                record.put("msg.type", type);
            }
            record.put(FIELD, body);
            record.put("user", "alice");
            out.add(record);
        }
        return out;
    }

    @Test
    void formatNamesAreStructuredForXmlAndJsonOnly() {
        for (final String structured : List.of("xml", "JSON", "text/xml", "application/xml", "application/json",
                "application/soap+xml; charset=utf-8", "application/vnd.api+json", "application/atom+xml")) {
            assertThat(HeldMarkup.structuredFormat(structured)).as(structured).isTrue();
        }
        for (final String document : List.of("html", "HTML", "text", "rtf", "markdown", "text/html",
                "application/xhtml+xml", "text/plain", "text/rtf", "application/rtf", "text/markdown",
                "text/enriched", "text/richtext", "message/rfc822", "multipart/alternative", "application/pdf",
                "application/octet-stream", "image/png", "application/x-new-thing")) {
            assertThat(HeldMarkup.structuredFormat(document)).as(document).isFalse();
        }
        for (final String other : List.of("alice", "Login", "", "10.0.0.1", "text/", "a/b")) {
            assertThat(HeldMarkup.structuredFormat(other)).as(other).isNull();
        }
        assertThat(HeldMarkup.structuredFormat(null)).isNull();
    }

    @Test
    void aSiblingNamingTheFormatDecides() {
        final List<String> html = List.of("<div><p>a</p></div>", "<div><p>b</p></div>");
        assertThat(HeldMarkup.judge(FIELD, records(html, "application/xml"), null).kind())
                .isEqualTo(HeldMarkup.Kind.STRUCTURED);
        final HeldMarkup.Verdict verdict = HeldMarkup.judge(FIELD, records(html, "text/html"), null);
        assertThat(verdict.kind()).isEqualTo(HeldMarkup.Kind.DOCUMENT);
        assertThat(verdict.sibling()).isEqualTo("msg.type");
        assertThat(verdict.samples()).isEmpty();
        // A field one level up or down isn't a sibling: compared by the names' keys, never by what they're called.
        final List<Map<String, Object>> nested = records(html, null);
        nested.forEach(record -> record.put("type", "text/html"));
        assertThat(HeldMarkup.judge(FIELD, nested, null).kind()).isEqualTo(HeldMarkup.Kind.HTML);
    }

    @Test
    void htmlIsToldByItsElementsWrittenInOneCase() {
        assertThat(HeldMarkup.judge(FIELD, records(List.of("<DIV><P>a</P></DIV>", "<DIV><P>b</P></DIV>"), null),
                null).kind()).isEqualTo(HeldMarkup.Kind.HTML);
        // A SOAP envelope's Header and Body are XML's names (capitalised), not HTML's.
        final String soap = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Header/>"
                            + "<soap:Body><Login><User>u</User></Login></soap:Body></soap:Envelope>";
        assertThat(HeldMarkup.judge(FIELD, records(List.of(soap, soap), null), null).kind())
                .isEqualTo(HeldMarkup.Kind.STRUCTURED);
        // A root HTML names, its other elements not: structure decides.
        final String data = "<data><user>u</user><host>h</host><port>1</port></data>";
        assertThat(HeldMarkup.judge(FIELD, records(List.of(data, data), null), null).kind())
                .isEqualTo(HeldMarkup.Kind.STRUCTURED);
    }

    @Test
    void structureIsComparedWithinEachEventType() {
        // Each record unlike the others of its event type: varied, said as within the event types.
        final List<String> bodies = List.of("<r><a>1</a></r>", "<r><b><c>2</c></b></r>", "<r><d/><e/></r>",
                "<r><f>3</f></r>", "<r><g><h/></g></r>", "<r><i/></r>");
        final List<Map<String, Object>> read = records(bodies, null);
        for (int n = 0; n < read.size(); n++) {
            read.get(n).put("kind", n % 2 == 0
                    ? "x"
                    : "y");
        }
        final HeldMarkup.Verdict verdict = HeldMarkup.judge(FIELD, read, record -> record.get("kind"));
        assertThat(verdict.kind()).isEqualTo(HeldMarkup.Kind.VARIED_IN_TYPE);
        assertThat(verdict.note(FIELD)).contains("of one event type", "confirm with the user");
        assertThat(verdict.samples()).hasSize(3);
        // Alike within each event type though unlike across them: structured.
        final List<String> typed = List.of("<r><a>1</a></r>", "<r><b><c>2</c></b></r>", "<r><a>3</a></r>",
                "<r><b><c>4</c></b></r>");
        final List<Map<String, Object>> two = records(typed, null);
        for (int n = 0; n < two.size(); n++) {
            two.get(n).put("kind", n % 2 == 0
                    ? "a"
                    : "b");
        }
        assertThat(HeldMarkup.judge(FIELD, two, record -> record.get("kind")).kind())
                .isEqualTo(HeldMarkup.Kind.STRUCTURED);
    }

    @Test
    void samplesAreShortAndOnOneLine() {
        final String longBody = "<r>\n  <a>" + "x".repeat(400) + "</a>\n</r>";
        final HeldMarkup.Verdict verdict = HeldMarkup.judge(FIELD, records(List.of(longBody), null), null);

        assertThat(verdict.kind()).isEqualTo(HeldMarkup.Kind.UNCLEAR);
        assertThat(verdict.samples()).singleElement().asString().startsWith("<r> <a>xxx").endsWith("...")
                .hasSize(203);
        assertThat(verdict.mapped()).isFalse();
        assertThat(verdict.note(FIELD)).contains("ask the user");
    }
}
