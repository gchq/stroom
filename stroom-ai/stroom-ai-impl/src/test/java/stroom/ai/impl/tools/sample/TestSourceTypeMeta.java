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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A source type field read from a stream part's meta: `meta:<Attribute>`, its value laid over each of the part's
/// records as a field of theirs.
class TestSourceTypeMeta {

    @Test
    void aMetaFieldNamesItsAttribute() {
        assertThat(SourceTypeMeta.isMeta("meta:ServedRoles")).isTrue();
        assertThat(SourceTypeMeta.isMeta("META:ServedRoles")).isTrue();
        assertThat(SourceTypeMeta.attribute("meta: ServedRoles ")).isEqualTo("ServedRoles");
        assertThat(SourceTypeMeta.isMeta("meta:")).isFalse();
        assertThat(SourceTypeMeta.isMeta("metadata.role")).isFalse();
        assertThat(SourceTypeMeta.isMeta(null)).isFalse();
        assertThat(SourceTypeMeta.attribute("sourcetype")).isNull();
        assertThat(SourceTypeMeta.field("ServedRoles")).isEqualTo("meta:ServedRoles");
    }

    @Test
    void aPartsValueIsLaidOverItsRecordsAsStroomMetaReadsItIgnoringCase() {
        final Map<String, Object> fields = SourceTypeMeta.partFields("meta:ServedRoles", Map.of("Feed", "WIN",
                "servedroles", "DNS Server, Directory Services"));

        assertThat(fields).containsExactly(Map.entry("meta:ServedRoles", "DNS Server, Directory Services"));
        assertThat(SourceTypeMeta.partFields("meta:ServedRoles", Map.of("Feed", "WIN"))).isEmpty();
        assertThat(SourceTypeMeta.partFields("sourcetype", Map.of("sourcetype", "x"))).isEmpty();
        final Map<String, Object> record = SourceTypeMeta.withPart(Map.of("EventID", "4624"), fields);
        assertThat(record).containsEntry("EventID", "4624").containsKey("meta:ServedRoles");
        assertThat(SourceTypeFields.valueOf(record, "meta:ServedRoles")).isEqualTo("DNS Server, Directory Services");
        // A text record (no fields) has the part's; a part with none leaves a record as it is.
        assertThat(SourceTypeMeta.withPart(null, fields)).isEqualTo(fields);
        assertThat(SourceTypeMeta.withPart(null, Map.of())).isNull();
        // Never a record's message, however long and wordy.
        assertThat(SourceTypeFields.messageField(SourceTypeMeta.withPart(Map.of("id", "1"), Map.of(
                "meta:Roles", "DNS Server, File Server, Directory Services, Print Server")))).isNull();
    }

    @Test
    void theAttributesPartsCarryAreSaidCommonestFirst() {
        assertThat(SourceTypeMeta.carried(List.of("Feed", "ServedRoles"), "servedroles")).hasValue("ServedRoles");
        assertThat(SourceTypeMeta.carried(List.of("Feed"), "ServedRoles")).isEmpty();
        assertThat(SourceTypeMeta.commonest(List.of(Map.of("Feed", "a", "RemoteHost", "h1"),
                Map.of("Feed", "a", "remotehost", "h2"), Map.of("Feed", "a", "Compression", "zip"))))
                .containsExactly("Feed (3 parts)", "RemoteHost (2 parts)", "Compression (1 part)");
    }
}
