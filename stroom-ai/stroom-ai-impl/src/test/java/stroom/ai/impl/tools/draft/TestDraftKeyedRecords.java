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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// The draft of records of lines tied by a key (auditd): the time from the key's epoch, the key and each line's
/// copy of it left out, a rule per first line's name, each line's fields read under its name.
class TestDraftKeyedRecords {

    private static final String AUDITD = resource("/stroom/ai/impl/tools/sample/auditd-lines.log");

    @Test
    void theKeysEpochIsTheTimeAndTheFirstLinesNameTheKind() {
        final SplitterSpec spec = SplitterDryRun.inferSpec(AUDITD).spec();
        final Map<String, Object> draft = DraftMapping.draft(List.of(AUDITD), "Auditd", "Auditd", "Test", null, null,
                spec);
        @SuppressWarnings("unchecked") final Map<String, Object> mapping = (Map<String, Object>) draft.get("mapping");

        assertThat(String.valueOf(mapping.get("common"))).contains("{path=EventTime/TimeCreated, "
                                                                   + "xpath=replace(data[@name='msg']/@value, "
                                                                   + "'^\\D*(\\d{10}(\\.\\d{1,9})?).*$', '$1'), "
                                                                   + "time_format=epoch_s}",
                "{path=EventDetail/TypeId, field=type}");
        assertThat(String.valueOf(draft.get("notes"))).contains("its epoch time is EventTime/TimeCreated")
                .doesNotContain("No timestamp field");
        // The key is no description, and no line's copy of it is a field.
        assertThat(String.valueOf(mapping)).doesNotContain("field=msg", "/msg")
                .contains("field=SYSCALL/exe", "field=EXECVE/a0", "field=USER_LOGIN/acct");
        assertThat(String.valueOf(mapping.get("events"))).contains("equals=SYSCALL", "equals=USER_LOGIN");
    }

    @Test
    void aKeyWithNoTimeLeavesTheTimeToALinesField() {
        final String lines = "type=A id=k1 when=2026-10-11T10:00:00Z x=1\ntype=B id=k1 y=2\n"
                             + "type=A id=k2 when=2026-10-11T10:00:05Z x=3\ntype=B id=k2 y=4\n"
                             + "type=A id=k3 when=2026-10-11T10:00:09Z x=5\ntype=B id=k3 y=6\n";
        final SplitterSpec spec = SplitterSpec.builder("key_value").delimiter(" ").recordKey("(?<!\\S)id=(\\S+)")
                .keyField("id").lineName("\\s*type=([A-Za-z_][\\w.-]*)\\s*").leadField("type").build();
        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "App", "App", "Test", null, null, spec);

        assertThat(String.valueOf(draft.get("notes"))).contains("it holds no time");
        assertThat(String.valueOf(draft.get("mapping"))).contains("A/when").doesNotContain("field=id");
    }

    private static String resource(final String name) {
        try (InputStream in = Objects.requireNonNull(TestDraftKeyedRecords.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
