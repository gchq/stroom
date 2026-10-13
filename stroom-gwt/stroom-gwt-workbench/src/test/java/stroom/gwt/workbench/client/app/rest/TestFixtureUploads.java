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

package stroom.gwt.workbench.client.app.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFixtureUploads {

    private static final String URL = "importfile.rpc";

    private final FakeScheduler scheduler = new FakeScheduler();
    private final List<String> events = new ArrayList<>();
    private final List<String> callbacks = new ArrayList<>();
    private final TestUploadReply.RecordingCallback callback = new TestUploadReply.RecordingCallback(callbacks);

    @BeforeEach
    void setUp() {
        scheduler.tasks.clear();
        events.clear();
        callbacks.clear();
    }

    @Test
    void testReplyIsAsynchronousAndDelayed() {
        final FixtureUploads uploads = uploads(RestFixtures.builder()
                .upload(UploadReply.success("res-1", "import.zip").delayed(300))
                .build());

        uploads.upload(upload("import.zip"), callback);

        assertThat(events).containsExactly("upload POST importfile.rpc (upload of import.zip)");
        assertThat(callbacks).isEmpty();
        assertThat(uploads.getPendingCount()).isOne();
        assertThat(scheduler.tasks.get(0).delayMillis).isEqualTo(300);

        scheduler.runAll();

        assertThat(uploads.getPendingCount()).isZero();
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.get(0)).startsWith("success #PM#").contains("key=res-1");
    }

    @Test
    void testZeroDelayIsStillAsynchronous() {
        final FixtureUploads uploads = uploads(RestFixtures.builder()
                .upload(UploadReply.failure("Bad"))
                .build());

        uploads.upload(upload("a.txt"), callback);

        assertThat(callbacks).isEmpty();
        assertThat(scheduler.tasks.get(0).delayMillis).isEqualTo(1);
    }

    @Test
    void testSequence_lastRepeats() {
        final FixtureUploads uploads = uploads(RestFixtures.builder()
                .upload(UploadReply.networkError("First"), UploadReply.networkError("Second"))
                .build());

        uploads.upload(upload("a.txt"), callback);
        uploads.upload(upload("a.txt"), callback);
        uploads.upload(upload("b.txt"), callback);
        scheduler.runAll();

        assertThat(callbacks).containsExactly("failure First", "failure Second", "failure Second");
    }

    @Test
    void testSequenceStartsAgainInANewSession() {
        final RestFixtures fixtures = RestFixtures.builder()
                .upload(UploadReply.networkError("First"), UploadReply.networkError("Second"))
                .build();

        uploads(fixtures).upload(upload("a.txt"), callback);
        uploads(fixtures).upload(upload("a.txt"), callback);
        scheduler.runAll();

        assertThat(callbacks).containsExactly("failure First", "failure First");
    }

    @Test
    void testMatchesByFileName() {
        final FixtureUploads uploads = uploads(RestFixtures.builder()
                .upload("bad.zip", UploadReply.failure("Not a zip"))
                .upload(UploadReply.networkError("Other"))
                .build());

        uploads.upload(upload("good.zip"), callback);
        uploads.upload(upload("bad.zip"), callback);
        scheduler.runAll();

        assertThat(callbacks.get(0)).isEqualTo("failure Other");
        assertThat(callbacks.get(1)).contains("exception=Not\\sa\\szip");
    }

    @Test
    void testUnhandledUpload_strict() {
        final FixtureUploads uploads = uploads(RestFixtures.none());

        uploads.upload(upload("a.txt"), callback);
        scheduler.runAll();

        assertThat(events).containsExactly(
                "upload POST importfile.rpc (upload of a.txt)",
                "unhandled POST importfile.rpc (upload of a.txt) strict=true");
        // As Stroom's transport reports a 404
        assertThat(callbacks).containsExactly("failure Upload failed (HTTP 404)");
    }

    @Test
    void testUnhandledUpload_lenient() {
        final FixtureUploads uploads = uploads(RestFixtures.builder()
                .upload("other.txt", UploadReply.success("k", "other.txt"))
                .lenient()
                .build());

        uploads.upload(upload("a.txt"), callback);

        assertThat(events).contains("unhandled POST importfile.rpc (upload of a.txt) strict=false");
    }

    @Test
    void testFollowedBy_keepsTheUploadRoutesInOrder() {
        final RestFixtures story = RestFixtures.builder().upload(UploadReply.networkError("Story")).build();
        final RestFixtures fallback = RestFixtures.builder().upload(UploadReply.networkError("Fallback")).build();
        final FixtureUploads uploads = uploads(story.followedBy(fallback));

        uploads.upload(upload("a.txt"), callback);
        scheduler.runAll();

        assertThat(callbacks).containsExactly("failure Story");
    }

    @Test
    void testAddAll_includesTheUploadRoutes() {
        final RestFixtures shared = RestFixtures.builder().upload(UploadReply.networkError("Shared")).build();
        final FixtureUploads uploads = uploads(RestFixtures.builder().addAll(shared).build());

        uploads.upload(upload("a.txt"), callback);
        scheduler.runAll();

        assertThat(callbacks).containsExactly("failure Shared");
    }

    @Test
    void testDispose_cancelsPendingAndDropsLaterUploads() {
        final FixtureUploads uploads = uploads(RestFixtures.builder()
                .upload(UploadReply.success("k", "a.txt"))
                .build());

        uploads.upload(upload("a.txt"), callback);
        uploads.dispose();
        uploads.upload(upload("b.txt"), callback);
        scheduler.runAll();

        assertThat(uploads.isDisposed()).isTrue();
        assertThat(uploads.getPendingCount()).isZero();
        assertThat(callbacks).isEmpty();
        assertThat(events).containsExactly(
                "upload POST importfile.rpc (upload of a.txt)",
                "dropped POST importfile.rpc (upload of b.txt)");
    }

    @Test
    void testBuild_refusesUnreachableUploadRoutes() {
        assertThatThrownBy(() -> RestFixtures.builder()
                .upload(UploadReply.networkError("All"))
                .upload("a.txt", UploadReply.networkError("A"))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upload of a.txt can never reply");
        assertThatThrownBy(() -> RestFixtures.builder()
                .upload("a.txt", UploadReply.networkError("1"))
                .upload("a.txt", UploadReply.networkError("2"))
                .build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RestFixtures.builder()
                .upload(UploadReply.networkError("1"))
                .upload(UploadReply.networkError("2"))
                .build())
                .isInstanceOf(IllegalStateException.class);
        // A name, then all others, is fine; and routes added with addAll aren't checked
        final RestFixtures shared = RestFixtures.builder().upload(UploadReply.networkError("Shared")).build();
        assertThat(RestFixtures.builder()
                .upload("a.txt", UploadReply.networkError("A"))
                .upload(UploadReply.networkError("All"))
                .addAll(shared)
                .build()).isNotNull();
    }

    @Test
    void testBuilder_refusesNulls() {
        assertThatThrownBy(() -> RestFixtures.builder().upload((UploadReply) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RestFixtures.builder().upload(UploadReply.networkError("x"), (UploadReply) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RestFixtures.builder().upload((String) null, UploadReply.networkError("x")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testRecordedUpload_textContent() {
        assertThat(RecordedUpload.textContent("<config/>", 9)).isEqualTo("<config/>");
        assertThat(RecordedUpload.textContent("", 0)).isEmpty();
        // Not UTF-8 (FileReader's replacement character), binary, too big or not read
        assertThat(RecordedUpload.textContent("PK��", 4)).isNull();
        assertThat(RecordedUpload.textContent("a\u0000b", 3)).isNull();
        assertThat(RecordedUpload.textContent("x", RecordedUpload.MAX_CONTENT_BYTES + 1)).isNull();
        assertThat(RecordedUpload.textContent("x", RecordedUpload.MAX_CONTENT_BYTES)).isEqualTo("x");
        assertThat(RecordedUpload.textContent(null, 1)).isNull();
    }

    @Test
    void testRecordedUpload() {
        final RecordedUpload upload = new RecordedUpload(URL, "a.txt", null);

        assertThat(upload.getUrl()).isEqualTo(URL);
        assertThat(upload.getFileName()).isEqualTo("a.txt");
        assertThat(upload.getContent()).isNull();
        assertThat(upload.describe()).isEqualTo("POST importfile.rpc (upload of a.txt)");
        assertThatThrownBy(() -> new RecordedUpload(null, "a", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RecordedUpload(URL, null, null)).isInstanceOf(NullPointerException.class);
    }

    private static RecordedUpload upload(final String fileName) {
        return new RecordedUpload(URL, fileName, "content");
    }

    private FixtureUploads uploads(final RestFixtures fixtures) {
        return new FixtureUploads(fixtures, scheduler, new FixtureUploads.Listener() {
            @Override
            public void onUpload(final RecordedUpload upload) {
                events.add("upload " + upload.describe());
            }

            @Override
            public void onUnhandledUpload(final RecordedUpload upload, final String message, final boolean strict) {
                events.add("unhandled " + upload.describe() + " strict=" + strict);
            }

            @Override
            public void onDroppedUpload(final RecordedUpload upload) {
                events.add("dropped " + upload.describe());
            }
        });
    }

    // --------------------------------------------------------------------------------


    private static final class FakeScheduler implements ReplyScheduler {

        private final List<Task> tasks = new ArrayList<>();

        @Override
        public Scheduled schedule(final int delayMillis, final Runnable runnable) {
            final Task task = new Task(delayMillis, runnable);
            tasks.add(task);
            return () -> task.cancelled = true;
        }

        private void runAll() {
            for (final Task task : new ArrayList<>(tasks)) {
                if (!task.cancelled && !task.done) {
                    task.done = true;
                    task.runnable.run();
                }
            }
        }
    }

    // --------------------------------------------------------------------------------


    private static final class Task {

        private final int delayMillis;
        private final Runnable runnable;
        private boolean cancelled;
        private boolean done;

        private Task(final int delayMillis, final Runnable runnable) {
            this.delayMillis = delayMillis;
            this.runnable = runnable;
        }
    }
}
