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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The files upload_sample gave commands for, waiting to be posted, held in the database (MySQL, the test
/// database).
class TestSampleUploads {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private SampleUploadsDb db;
    private SampleUploads uploads;

    @BeforeEach
    void setUp() {
        db = SampleUploadsDb.emptied();
        uploads = db.uploads(AiConfig::new, "node1", now::get);
    }

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
        assertThat(uploads.claim(pending.id(), "alice", "001_1.json")).map(SampleUploads.Claim::pending)
                .contains(pending);
        // Posted once.
        assertThat(uploads.claim(pending.id(), "alice", "001_1.json")).isEmpty();
        assertThat(uploads.claim(pending.id(), "alice", "002.json")).map(SampleUploads.Claim::pending)
                .contains(pending);
    }

    @Test
    void aFileWhoseStoringFailedCanBePostedAgain() {
        final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a.json"));
        final SampleUploads.Claim claim = uploads.claim(pending.id(), "alice", "a.json").orElseThrow();

        uploads.release(claim);

        assertThat(uploads.claim(pending.id(), "alice", "a.json")).map(SampleUploads.Claim::pending)
                .contains(pending);
    }

    @Test
    void aStoredFileIsNotPostedAgain() {
        final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a.json"));
        final SampleUploads.Claim claim = uploads.claim(pending.id(), "alice", "a.json").orElseThrow();

        uploads.stored(claim, 77L);
        // Not even once its claim would have gone stale.
        now.addAndGet(SampleUploads.CLAIM_GOES_STALE.toMillis() + 1);

        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isEmpty();
        uploads.release(claim);
        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isEmpty();
    }

    @Test
    void aFileCutOffByItsNodeStoppingCanBePostedAgainOnceTheClaimIsStale() {
        final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a.json"));
        // node1 claims it, then stops part way through the post: it never stores or releases it.
        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isPresent();
        final SampleUploads otherNode = db.uploads(AiConfig::new, "node2", now::get);

        now.addAndGet(SampleUploads.CLAIM_GOES_STALE.toMillis() - 1);
        assertThat(otherNode.claim(pending.id(), "alice", "a.json")).isEmpty();
        now.addAndGet(2);
        final SampleUploads.Claim claim = otherNode.claim(pending.id(), "alice", "a.json").orElseThrow();

        otherNode.stored(claim, 77L);
        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isEmpty();
    }

    @Test
    void theCommandsAreTakenOnAnotherNodeAndAfterARestart() {
        final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events",
                Map.of("MyHost", "ws01"), "fortios", List.of("a.json"));
        // Another node, or this one after a restart: nothing is kept in memory.
        final SampleUploads otherNode = db.uploads(AiConfig::new, "node2", now::get);

        assertThat(otherNode.claim(pending.id(), "alice", "a.json")).map(SampleUploads.Claim::pending)
                .contains(pending);
        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isEmpty();
    }

    @Test
    void aFilePathTooLongToHoldIsRefused() {
        assertThatThrownBy(() -> uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a/".repeat(400) + "b.json")))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("longer than 700 characters");
    }

    @Test
    void aFileNamedTwiceIsOneFile() {
        final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a.json", "a.json"));

        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isPresent();
        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isEmpty();
    }

    @Test
    void theCommandsRunOut() {
        final SampleUploads.Pending pending = uploads.issue("alice", "F", "f1", "Raw Events", Map.of(), null,
                List.of("a.json", "b.json"));
        assertThat(pending.expiresMs()).isEqualTo(1_000_000L + 15 * 60_000L);

        final SampleUploads.Claim claim = uploads.claim(pending.id(), "alice", "b.json").orElseThrow();

        now.set(pending.expiresMs());

        assertThat(uploads.claim(pending.id(), "alice", "a.json")).isEmpty();
        // A file released after the commands ran out isn't taken either.
        uploads.release(claim);
        assertThat(uploads.claim(pending.id(), "alice", "b.json")).isEmpty();
        // Sets that have run out are deleted by the 'AI Chat Retention' job.
        assertThat(db.dao().deleteExpired(now.get())).isEqualTo(1);
    }

    @Test
    void aUserCanHaveOnlySoManySetsOfCommandsWaiting() {
        // Each set is kept until it runs out: one user can't fill the database with them.
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
