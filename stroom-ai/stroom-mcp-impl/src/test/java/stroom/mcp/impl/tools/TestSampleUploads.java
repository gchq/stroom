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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The files upload_sample gave commands for, waiting to be posted (in place of the Stroom MCP server's upload
/// tickets, `utils/uploads.py` `UploadTickets`, and its tests in `tests/test_uploads.py`).
class TestSampleUploads {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final SampleUploads uploads = new SampleUploads(AiConfig::new, now::get);

    @Test
    void eachFileIsTakenOnceByTheUserItWasGivenTo() {
        final SampleUploads.Pending pending = uploads.issue("alice", "FORTIOS-EVENTS-V1.0", "f1", "Raw Events",
                Map.of("MyHost", "ws01"), "fortios", List.of("001_1.json", "002.json"));

        // 12 characters, nothing to quote.
        assertThat(pending.id()).matches("[A-Za-z0-9_-]{12}");
        assertThat(uploads.claim(pending.id(), "bob", "001_1.json")).isEmpty();
        assertThat(uploads.claim(pending.id(), "alice", "003.json")).isEmpty();
        assertThat(uploads.claim("not-an-id-12", "alice", "001_1.json")).isEmpty();
        assertThat(uploads.claim(null, "alice", "001_1.json")).isEmpty();
        assertThat(uploads.claim(pending.id(), "alice", "001_1.json")).contains(pending);
        // Posted once.
        assertThat(uploads.claim(pending.id(), "alice", "001_1.json")).isEmpty();
        assertThat(uploads.claim(pending.id(), "alice", "002.json")).contains(pending);
    }

    @Test
    void aFileWhoseStoringFailedCanBePostedAgain() {
        final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a.json"));
        uploads.claim(pending.id(), "alice", "a.json");

        uploads.release(pending, "a.json");

        assertThat(uploads.claim(pending.id(), "alice", "a.json")).contains(pending);
    }

    @Test
    void theCommandsRunOut() {
        final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a.json", "b.json"));
        assertThat(pending.expiresMs()).isEqualTo(1_000_000L + 15 * 60_000L);

        now.set(pending.expiresMs());

        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isEmpty();
        // Issuing clears what has run out.
        uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null, List.of("c.json"));
        uploads.release(pending, "b.json");
        assertThat(uploads.claim(pending.id(), "alice", "b.json")).isEmpty();
    }

    @Test
    void aUserCanHaveOnlySoManySetsOfCommandsWaiting() {
        // Each set is kept in this node's memory until it runs out: one user can't fill it.
        final AiConfig aiConfig = new AiConfig();
        final int max = aiConfig.getSampleUploads().getMaxPendingPerUser();
        SampleUploads.Pending first = null;
        for (int i = 0; i < max; i++) {
            final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                    List.of("a.json"));
            first = first == null
                    ? pending
                    : first;
        }

        assertThatThrownBy(() -> uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a.json")))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("You have " + max + " sets of upload commands waiting");
        // Others have their own.
        assertThat(uploads.issue("bob", "F", "f1", "Raw Events", Map.of(), null, List.of("a.json"))).isNotNull();
        // Once a set's files are posted, it no longer counts.
        assertThat(uploads.claim(first.id(), "alice", "a.json")).isPresent();
        assertThat(uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null, List.of("a.json")))
                .isNotNull();
    }

    @Test
    void theLimitsAreConfigured() {
        assertThat(uploads.maxMb()).isEqualTo(100);
        assertThat(uploads.maxBytes()).isEqualTo(100L * 1024 * 1024);
        assertThat(uploads.lasts().getDuration().toMinutes()).isEqualTo(15);
        assertThat(SampleUploads.PATH).isEqualTo("/mcp/upload");
    }
}
