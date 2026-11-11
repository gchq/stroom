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


package stroom.ai.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiTabSnapshotPart {

    @Test
    void theHashOfTextIsStableAndChangesWithIt() {
        // 64-bit FNV-1a's offset, for nothing.
        assertThat(AiTabSnapshotPart.hashText("")).isEqualTo("cbf29ce484222325");
        assertThat(AiTabSnapshotPart.hashText(null)).isEqualTo(AiTabSnapshotPart.hashText(""));
        assertThat(AiTabSnapshotPart.hashText("Group by 'User'"))
                .isEqualTo(AiTabSnapshotPart.hashText("Group by 'User'"))
                .isNotEqualTo(AiTabSnapshotPart.hashText("Group by 'Users'"))
                .matches("[0-9a-f]{1,16}");
        // Characters beyond one byte count in full.
        assertThat(AiTabSnapshotPart.hashText(String.valueOf((char) 0x100)))
                .isNotEqualTo(AiTabSnapshotPart.hashText(String.valueOf((char) 0)));
    }

    @Test
    void theHashOfLinesIgnoresHowTheyAreLaidOut() {
        assertThat(AiTabSnapshotPart.hashLines("AND\n  EventType = Logon\n"))
                .isEqualTo(AiTabSnapshotPart.hashLines("  AND\nEventType = Logon"))
                .isEqualTo(AiTabSnapshotPart.hashLines("AND\n\n    EventType = Logon  \n\n"))
                .isNotEqualTo(AiTabSnapshotPart.hashLines("AND\n  EventType = Logoff\n"))
                .isNotEqualTo(AiTabSnapshotPart.hashLines("AND EventType = Logon"));
        assertThat(AiTabSnapshotPart.hashLines(null)).isEqualTo(AiTabSnapshotPart.hashLines(""));
    }
}
