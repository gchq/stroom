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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/// What a long tool call tells the client as it goes (asked for by the user: describe_document on an index ran for
/// minutes with nothing to show what it was doing).
class TestCallProgress {

    private static final ToolSession SESSION = new ToolSession(ToolSession.Kind.MCP, "s1",
            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));

    @Test
    void oneCallsProgressIsTheSameWhereverItIsAskedFor() {
        final List<String> sent = new ArrayList<>();
        final ToolCallContext context = ToolCallContext.builder(SESSION).progress(sent::add).build();
        CallProgress.of(context).say("one");
        // E.g. write_documentation surveying the index a second time.
        CallProgress.of(context).say("two");
        assertThat(CallProgress.of(context)).isSameAs(CallProgress.of(context));
        assertThat(sent).containsExactly("one", "two");
        // Another call's is its own.
        assertThat(CallProgress.of(ToolCallContext.builder(SESSION).build())).isNotSameAs(CallProgress.of(context));
    }

    @Test
    void progressNeverFailsTheCall() {
        final ToolCallContext broken = ToolCallContext.builder(SESSION).progress(message -> {
            throw new IllegalStateException("the client went away");
        }).build();
        assertThatCode(() -> CallProgress.of(broken).say("still here")).doesNotThrowAnyException();
        // No progress asked for: nothing is sent, and nothing fails.
        assertThatCode(() -> CallProgress.of(ToolCallContext.builder(SESSION).build()).say("no one listening"))
                .doesNotThrowAnyException();
    }

    @Test
    void aLoopSaysWhereItIsOnlyNowAndThen() {
        // Asked for by the user: not an update for each and every record stepped.
        final List<String> sent = new ArrayList<>();
        final AtomicLong now = new AtomicLong();
        final CallProgress progress = new CallProgress(sent::add, now::get);
        progress.say("Stepping stream 7 (1 of 1) through ACME: up to 50 records");
        for (int record = 1; record <= 7; record++) {
            // Each step takes 2 s.
            now.addAndGet(2_000);
            progress.nowAndThen("Stepping stream 7 (1 of 1): " + record + " of up to 50 records stepped");
        }
        assertThat(sent).containsExactly("Stepping stream 7 (1 of 1) through ACME: up to 50 records",
                "Stepping stream 7 (1 of 1): 3 of up to 50 records stepped",
                "Stepping stream 7 (1 of 1): 6 of up to 50 records stepped");
        // The first turn of a loop with nothing said before it is said.
        final List<String> first = new ArrayList<>();
        new CallProgress(first::add, now::get).nowAndThen("Finding streams", Duration.ofSeconds(5));
        assertThat(first).containsExactly("Finding streams");
    }
}
