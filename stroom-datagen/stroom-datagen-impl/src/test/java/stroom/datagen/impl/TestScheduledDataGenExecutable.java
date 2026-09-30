/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.datagen.impl;

import stroom.analytics.impl.ScheduledExecutorService.ExecutionResult;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestScheduledDataGenExecutable {

    private static final Instant EXECUTION_TIME = Instant.parse("2026-08-20T10:15:30.000Z");
    private static final Instant EFFECTIVE_EXECUTION_TIME = Instant.parse("2026-08-20T10:00:00.000Z");
    private static final String FEED_NAME = "TEST-FEED";
    private static final String TEMPLATE = "some generated data";

    private static final DocRef FEED_DOC_REF = DocRef.builder()
            .type(FeedDoc.TYPE)
            .uuid("feed-uuid-1")
            .name(FEED_NAME)
            .build();

    @Mock
    private DataGenStore dataGenStore;
    @Mock
    private Store streamStore;
    @Mock
    private FeedStore feedStore;
    @Mock
    private Target target;
    @Mock
    private OutputStreamProvider outputStreamProvider;

    private final CapturingSegmentOutputStream outputStream = new CapturingSegmentOutputStream();

    private ScheduledDataGenExecutable executable;

    @BeforeEach
    void setUp() {
        executable = new ScheduledDataGenExecutable(dataGenStore, streamStore, feedStore);
    }


    // --------------------------------------------------------------------------------
    // run() - guards against a partially configured doc.
    //
    // These matter because the caller (ScheduledExecutorService) catches any exception
    // thrown from run(), records ExecutionResult("Error", e.getMessage()) and then
    // DISABLES the execution schedule. An NPE therefore produces a history row with a
    // null message and a silently disabled generator. Returning an explicit error
    // instead gives the user a reason and leaves the schedule enabled to retry.
    // --------------------------------------------------------------------------------

    @Test
    void run_feedNotSet_returnsErrorAndWritesNothing() {
        final DataGenDoc doc = docBuilder()
                .template(TEMPLATE)
                .build();

        final ExecutionResult result = run(doc);

        assertThat(result.status())
                .isEqualTo(ExecutionResult.STATUS_ERROR);
        assertThat(result.message())
                .describedAs("The message is what the user sees in the execution history")
                .isNotBlank()
                .containsIgnoringCase("feed");
        verifyNoInteractions(streamStore);
    }

    @Test
    void run_templateNotSet_returnsErrorAndWritesNothing() {
        final DataGenDoc doc = docBuilder()
                .feed(FEED_DOC_REF)
                .build();

        final ExecutionResult result = run(doc);

        assertThat(result.status())
                .isEqualTo(ExecutionResult.STATUS_ERROR);
        assertThat(result.message())
                .isNotBlank()
                .containsIgnoringCase("template");
        verifyNoInteractions(streamStore);
    }

    @Test
    void run_feedHasBeenDeleted_returnsErrorAndWritesNothing() {
        when(feedStore.readDocument(FEED_DOC_REF))
                .thenThrow(new DocumentNotFoundException(FEED_DOC_REF));

        final ExecutionResult result = run(fullyConfiguredDoc());

        assertThat(result.status())
                .isEqualTo(ExecutionResult.STATUS_ERROR);
        assertThat(result.message())
                .isNotBlank()
                .containsIgnoringCase("no longer exists");
        verifyNoInteractions(streamStore);
    }

    @Test
    void run_feedHasNoName_returnsErrorAndWritesNothing() {
        when(feedStore.readDocument(FEED_DOC_REF)).thenReturn(feedDoc(null));

        final ExecutionResult result = run(fullyConfiguredDoc());

        assertThat(result.status())
                .isEqualTo(ExecutionResult.STATUS_ERROR);
        assertThat(result.message())
                .isNotBlank()
                .containsIgnoringCase("has no name");
        verifyNoInteractions(streamStore);
    }


    // --------------------------------------------------------------------------------
    // run() - the happy path.
    // --------------------------------------------------------------------------------

    @Test
    void run_docFullyConfigured_writesTemplateToNewRawEventsStream() {
        givenStreamStoreAcceptsAWrite();
        final DataGenDoc doc = fullyConfiguredDoc();

        final ExecutionResult result = run(doc);

        final ArgumentCaptor<MetaProperties> captor = ArgumentCaptor.forClass(MetaProperties.class);
        verify(streamStore).openTarget(captor.capture());
        final MetaProperties metaProperties = captor.getValue();

        assertThat(metaProperties.getFeedName())
                .isEqualTo(FEED_NAME);
        assertThat(metaProperties.getTypeName())
                .isEqualTo(StreamTypeNames.RAW_EVENTS);
        assertThat(metaProperties.getEffectiveMs())
                .describedAs("The stream is stamped with the effective (scheduled) time, not the actual one")
                .isEqualTo(EFFECTIVE_EXECUTION_TIME.toEpochMilli());

        assertThat(outputStream.getContentAsUtf8())
                .isEqualTo(TEMPLATE);
        assertThat(result.status())
                .isNotEqualTo(ExecutionResult.STATUS_ERROR);
    }

    @Test
    void run_docFullyConfigured_closesTargetAndOutputStreamProvider() throws IOException {
        givenStreamStoreAcceptsAWrite();

        run(fullyConfiguredDoc());

        // Both are Closeable and hold a lock on the stream until closed.
        verify(outputStreamProvider).close();
        verify(target).close();
    }

    /**
     * Regression test for a renamed feed. The name in the doc's {@link DocRef} is whatever the feed
     * was called when it was picked; {@link MetaProperties} resolves the destination by name and
     * creates a feed for any name it does not know, so using the stale name would silently divert
     * the data into a brand new feed named after the old one.
     */
    @Test
    void run_feedHasBeenRenamed_usesTheLiveNameNotTheStoredOne() {
        when(feedStore.readDocument(FEED_DOC_REF)).thenReturn(feedDoc("RENAMED-FEED"));
        when(streamStore.openTarget(any())).thenReturn(target);
        when(target.next()).thenReturn(outputStreamProvider);
        when(outputStreamProvider.get()).thenReturn(outputStream);

        run(fullyConfiguredDoc());

        final ArgumentCaptor<MetaProperties> captor = ArgumentCaptor.forClass(MetaProperties.class);
        verify(streamStore).openTarget(captor.capture());
        assertThat(captor.getValue().getFeedName())
                .describedAs("Stored DocRef name is '%s', live feed name is 'RENAMED-FEED'", FEED_NAME)
                .isEqualTo("RENAMED-FEED");
    }

    @Test
    void run_templateHasNonAsciiCharacters_writesUtf8Bytes() {
        givenStreamStoreAcceptsAWrite();
        final String template = "café 日本語";
        final DataGenDoc doc = docBuilder()
                .feed(FEED_DOC_REF)
                .template(template)
                .build();

        run(doc);

        assertThat(outputStream.getBytes())
                .describedAs("Must be UTF-8, not the platform default charset")
                .isEqualTo(template.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void run_writeFails_throwsUncheckedIOException() {
        givenStreamStoreAcceptsAWrite();
        outputStream.failWith(new IOException("Disk full"));

        assertThatThrownBy(() -> run(fullyConfiguredDoc()))
                .isInstanceOf(UncheckedIOException.class)
                .hasRootCauseMessage("Disk full");
    }


    // --------------------------------------------------------------------------------
    // getDocs()
    // --------------------------------------------------------------------------------

    @Test
    void getDocs_oneDocFailsToLoad_returnsTheRest() {
        final DocRef refA = dataGenDocRef("a");
        final DocRef refB = dataGenDocRef("b");
        final DocRef refC = dataGenDocRef("c");
        final DataGenDoc docA = docBuilder().uuid("a").name("A").build();
        final DataGenDoc docC = docBuilder().uuid("c").name("C").build();

        when(dataGenStore.list()).thenReturn(List.of(refA, refB, refC));
        when(dataGenStore.readDocument(refA)).thenReturn(docA);
        when(dataGenStore.readDocument(refB)).thenThrow(new RuntimeException("Boom"));
        when(dataGenStore.readDocument(refC)).thenReturn(docC);

        assertThat(executable.getDocs())
                .describedAs("One bad doc must not stop the others from being scheduled")
                .containsExactly(docA, docC);
    }

    @Test
    void getDocs_docReadsAsNull_isExcluded() {
        final DocRef refA = dataGenDocRef("a");
        final DocRef refB = dataGenDocRef("b");
        final DataGenDoc docA = docBuilder().uuid("a").name("A").build();

        when(dataGenStore.list()).thenReturn(List.of(refA, refB));
        when(dataGenStore.readDocument(refA)).thenReturn(docA);
        when(dataGenStore.readDocument(refB)).thenReturn(null);

        assertThat(executable.getDocs())
                .containsExactly(docA);
    }

    @Test
    void getDocs_noDocs_returnsEmptyList() {
        when(dataGenStore.list()).thenReturn(List.of());

        assertThat(executable.getDocs())
                .isEmpty();
    }


    // --------------------------------------------------------------------------------
    // getIdentity()
    // --------------------------------------------------------------------------------

    @Test
    void getIdentity_returnsNameAndUuid() {
        final DataGenDoc doc = docBuilder()
                .uuid("doc-uuid-1")
                .name("My Generator")
                .build();

        assertThat(executable.getIdentity(doc))
                .isEqualTo("My Generator (doc-uuid-1)");
    }

    @Test
    void getIdentity_nullDoc_returnsNull() {
        assertThat(executable.getIdentity(null))
                .isNull();
    }


    // --------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------

    private ExecutionResult run(final DataGenDoc doc) {
        return executable.run(
                doc,
                null, // trigger - not used by run()
                EXECUTION_TIME,
                EFFECTIVE_EXECUTION_TIME,
                null, // executionSchedule - not used by run()
                null, // currentTracker - not used by run()
                ExecutionResult.empty());
    }

    private void givenStreamStoreAcceptsAWrite() {
        when(feedStore.readDocument(FEED_DOC_REF)).thenReturn(feedDoc(FEED_NAME));
        when(streamStore.openTarget(any())).thenReturn(target);
        when(target.next()).thenReturn(outputStreamProvider);
        when(outputStreamProvider.get()).thenReturn(outputStream);
    }

    private static DataGenDoc fullyConfiguredDoc() {
        return docBuilder()
                .feed(FEED_DOC_REF)
                .template(TEMPLATE)
                .build();
    }

    private static FeedDoc feedDoc(final String name) {
        return FeedDoc.builder()
                .uuid(FEED_DOC_REF.getUuid())
                .name(name)
                .build();
    }

    private static DataGenDoc.Builder docBuilder() {
        return DataGenDoc.builder()
                .uuid("doc-uuid-1")
                .name("My Generator");
    }

    private static DocRef dataGenDocRef(final String uuid) {
        return DataGenDoc.buildDocRef()
                .uuid(uuid)
                .build();
    }


    // --------------------------------------------------------------------------------


    /**
     * {@link SegmentOutputStream} is an abstract class rather than an interface, so a small
     * capturing double is clearer here than a Mockito mock - it lets the test assert on the
     * exact bytes written.
     */
    private static class CapturingSegmentOutputStream extends SegmentOutputStream {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private IOException writeException;

        private void failWith(final IOException writeException) {
            this.writeException = writeException;
        }

        private byte[] getBytes() {
            return out.toByteArray();
        }

        private String getContentAsUtf8() {
            return out.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void write(final int b) throws IOException {
            throwIfFailing();
            out.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            throwIfFailing();
            out.write(b, off, len);
        }

        private void throwIfFailing() throws IOException {
            if (writeException != null) {
                throw writeException;
            }
        }

        @Override
        public void addSegment() {
            // Not used by ScheduledDataGenExecutable.
        }

        @Override
        public void addSegment(final long position) {
            // Not used by ScheduledDataGenExecutable.
        }

        @Override
        public long getPosition() {
            return out.size();
        }
    }
}
