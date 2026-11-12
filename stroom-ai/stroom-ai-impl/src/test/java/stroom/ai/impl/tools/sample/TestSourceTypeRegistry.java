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

import stroom.ai.impl.tools.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The source type registry's document: read back as written, its block never shown as text.
class TestSourceTypeRegistry {

    @Test
    void aRegistryReadsBackAsWritten() {
        final Map<String, Object> registry = registry();

        final String text = SourceTypeRegistry.render(registry);

        assertThat(SourceTypeRegistry.read(text)).isEqualTo(ToolJson.loads(ToolJson.dumps(registry)));
        assertThat(SourceTypeRegistry.rows(SourceTypeRegistry.read(text))).containsExactly(
                new SourceTypeRows.Row("mail audit", SourceTypeRows.PATTERN, "^/var/log/mail/audit/"),
                new SourceTypeRows.Row("PegaDB", SourceTypeRows.VALUE, "PegaDB"));
    }

    @Test
    void itsReadablePartSaysTheFieldTheRowsAndWhy() {
        final String text = SourceTypeRegistry.render(registry());

        assertThat(text).startsWith("# ACME source types");
        assertThat(text).contains("aggregates 2 source type(s), told apart by the field `LogFilePath`");
        assertThat(text).contains("Environment: Prod; System Name by default: Acme");
        assertThat(text).contains("Parked: mail audit, PegaDB.");
        assertThat(text).contains("Why: noisy --> later.");
        assertThat(text).contains("| 1 | mail audit | ~ ^/var/log/mail/audit/ | Acme Mail |");
        assertThat(text).contains("| 2 | PegaDB | = PegaDB | Acme |");
        assertThat(text).contains("about 10.5% (5 read, about 50 in the streams read; from only 5 of the 48 "
                                  + "records read)");
        assertThat(text).contains("not seen in the 48 records read");
        // A `-->` in a value can't end the block.
        final String block = text.substring(text.indexOf("<!-- stroom-mcp source types"));
        assertThat(block.indexOf("-->")).isEqualTo(block.length() - "-->\n".length());
    }

    @Test
    void aDocWithNoBlockIsNoRegistry() {
        assertThat(SourceTypeRegistry.read("# x")).isNull();
        assertThat(SourceTypeRegistry.read(null)).isNull();
        assertThat(SourceTypeRegistry.docName("ACME")).isEqualTo("ACME source types");
    }

    private static Map<String, Object> registry() {
        final Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("feed", "ACME");
        registry.put("field", "LogFilePath");
        registry.put("field_by", "user");
        registry.put("environment", "Prod");
        registry.put("system_name", "Acme");
        registry.put("updated", "2026-11-12T10:00:00Z");
        final Map<String, Object> audit = row("mail audit", "pattern", "^/var/log/mail/audit/", "Acme Mail");
        audit.put("profile", map("records_read", 5, "share_percent", 10.5, "records_estimate", 50, "rests_on",
                "from only 5 of the 48 records read", "formats", map("key=value", 5), "suggested_category",
                "security audit", "confidence", "medium", "examples", List.of(map("stream", 1, "part", 0,
                        "record", 7))));
        final Map<String, Object> db = row("PegaDB", "value", "PegaDB", null);
        db.put("profile", map("records_read", 0, "rests_on", "not seen in the 48 records read"));
        registry.put("rows", List.of(audit, db));
        registry.put("scope", map("build", "b1", "source_types", List.of("mail audit"), "by", "user", "at",
                "2026-11-12T10:00:00Z"));
        return registry;
    }

    private static Map<String, Object> row(final String name,
                                           final String match,
                                           final String value,
                                           final String system) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("match", match);
        row.put("value", value);
        row.put("system_name", system);
        row.put("status", SourceTypeRegistry.PARKED);
        row.put("decided", map("by", "user", "at", "2026-11-12T10:00:00Z", "reason", "noisy --> later"));
        row.put("group", null);
        row.put("pipeline", null);
        row.put("output_feed", null);
        return row;
    }

    private static Map<String, Object> map(final Object... keysAndValues) {
        return JsonValues.dict(keysAndValues);
    }
}
