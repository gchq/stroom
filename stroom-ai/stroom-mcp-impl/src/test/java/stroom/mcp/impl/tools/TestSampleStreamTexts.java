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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Sample streams' texts read through a build's scope over an aggregated feed's source types.
class TestSampleStreamTexts {

    private static final SourceTypeScope DB = new SourceTypeScope("b", "source", List.of(
            new SourceTypeRows.Row("db", SourceTypeRows.VALUE, "db"),
            new SourceTypeRows.Row("web", SourceTypeRows.VALUE, "web")), List.of("db"));

    @Test
    void eachTextKeepsOnlyTheScopesRecordsAndATextOfNoneIsLeftOut() {
        final Map<String, String> texts = new LinkedHashMap<>();
        texts.put("stream 1", "{\"source\": \"db\", \"n\": 1}\n{\"source\": \"web\", \"n\": 2}\n");
        texts.put("stream 2", "{\"source\": \"web\", \"n\": 3}\n");
        texts.put("stream 3", "<13>Nov 12 10:00:00 host app: syslog, not records of a field\n");

        final List<SampleStreamTexts.Cut> cut = List.of(new SampleStreamTexts.Cut("stream 1", 1, 0),
                new SampleStreamTexts.Cut("stream 2", 2, 0));
        final SampleStreamTexts.Scoped scoped = SampleStreamTexts.inScope(new SampleStreamTexts.Texts(texts,
                List.of("a note"), cut), DB);

        assertThat(scoped.texts().texts()).containsOnlyKeys("stream 1", "stream 3");
        assertThat(scoped.texts().texts().get("stream 1")).isEqualTo("{\"source\": \"db\", \"n\": 1}\n");
        assertThat(scoped.texts().cut()).extracting(SampleStreamTexts.Cut::name).containsExactly("stream 1");
        assertThat(scoped.texts().notes()).contains("a note").anySatisfy(note -> assertThat(note)
                .startsWith("stream 3 can't be read record by record"));
        assertThat(scoped.said()).containsEntry("records_in_scope", 1)
                .containsEntry("records_left_out", Map.of("web", 2));
    }

    @Test
    void aSampleWithNoneOfTheScopesRecordsIsRefused() {
        final SampleStreamTexts.Texts texts = new SampleStreamTexts.Texts(Map.of("stream 2",
                "{\"source\": \"web\", \"n\": 3}\n"), List.of());

        assertThatThrownBy(() -> SampleStreamTexts.inScope(texts, DB)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("None of the sample's records is in build 'b''s scope (db)");
    }

    @Test
    void aScopeByAPartsMetaKeepsOrLeavesOutEachPartWholeByItsOwnValue() {
        final StreamReader reader = Mockito.mock(StreamReader.class);
        Mockito.when(reader.partAttributes(9L, 0)).thenReturn(Map.of("Roles", "DNS Server, Directory Services"));
        Mockito.when(reader.partAttributes(9L, 1)).thenReturn(Map.of("Roles", "File Server"));
        Mockito.when(reader.partAttributes(9L, 2)).thenReturn(Map.of());
        final SampleStreamTexts sampleTexts = new SampleStreamTexts(reader, Mockito.mock(DocumentAccess.class));
        final SourceTypeScope directory = new SourceTypeScope("b", "meta:Roles", List.of(new SourceTypeRows.Row(
                "Directory", SourceTypeRows.MEMBER, "Directory Services", ",")), List.of("Directory"));
        final Map<String, String> texts = new LinkedHashMap<>();
        // XML, which a record field's scope reads whole: kept or left out by its part's value.
        texts.put("stream 9 part 0", "<Events><Event><Id>1</Id></Event><Event><Id>2</Id></Event></Events>\n");
        texts.put("stream 9 part 1", "<Events><Event><Id>3</Id></Event></Events>\n");
        texts.put("stream 9 part 2", "<Events><Event><Id>4</Id></Event></Events>\n");

        final SampleStreamTexts.Scoped scoped = sampleTexts.scoped(new SampleStreamTexts.Texts(texts, List.of()),
                directory);

        assertThat(scoped.texts().texts()).containsOnlyKeys("stream 9 part 0");
        assertThat(scoped.said()).containsEntry("records_in_scope", 2).containsEntry("records_left_out",
                Map.of(SourceTypeScope.UNMATCHED, 2));
        assertThat(String.valueOf(scoped.said().get("note"))).contains("left out whole");
        assertThat(SampleStreamTexts.partOf("stream 9 part 2")).hasValueSatisfying(part -> {
            assertThat(part.stream()).isEqualTo(9L);
            assertThat(part.part()).isEqualTo(2);
        });
        assertThat(SampleStreamTexts.partOf("stream 7")).hasValueSatisfying(part -> assertThat(part.part())
                .isZero());
    }
}
