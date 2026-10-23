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

package stroom.pathways.impl;

import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Which failures this absorbs, and which it must not.
 *
 * <p>The work handed to it writes into a store and then deletes the only copy of what it wrote from,
 * so a failure of that work has to reach the caller. A failure to write the <em>report</em> about the
 * work must not, because a report nobody can write is no reason to stop learning. The two are told
 * apart by type: the consumer is a {@link java.util.function.Consumer}, so it cannot throw a checked
 * exception, and everything it fails with is unchecked.
 */
class TestMessageReceiverFactory {

    @Mock
    private Store streamStore;
    @Mock
    private Target target;
    @Mock
    private OutputStreamProvider outputStreamProvider;
    @Mock
    private SegmentOutputStream segmentOutputStream;

    private MessageReceiverFactory factory;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(streamStore.openTarget(any())).thenReturn(target);
        when(target.next()).thenReturn(outputStreamProvider);
        when(outputStreamProvider.get()).thenReturn(segmentOutputStream);
        factory = new MessageReceiverFactory(streamStore);
    }

    @Test
    void aFailureOfTheWorkReachesTheCaller() {
        assertThatThrownBy(() -> factory.create("INFO_FEED", messageReceiver -> {
            throw new IllegalStateException("the commit failed");
        }))
                .as("absorbing this would let the caller delete work that was never written")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("the commit failed");
    }

    @Test
    void aFailureOfTheReportStreamDoesNot() throws IOException {
        // Closing the stream is where a full or unwritable report surfaces, and by then the work has
        // already happened — so absorbing it is right, and telling the caller would be wrong.
        doThrow(new IOException("no room for the report")).when(segmentOutputStream).close();

        final boolean[] finished = {false};
        assertThatCode(() -> factory.create("INFO_FEED", messageReceiver -> finished[0] = true))
                .doesNotThrowAnyException();
        assertThat(finished[0]).as("the work still ran to completion").isTrue();
    }

    @Test
    void aFailureToWriteOneMessageDoesNotStopTheWork() {
        final boolean[] finished = {false};

        assertThatCode(() -> factory.create("INFO_FEED", messageReceiver -> {
            messageReceiver.log(stroom.util.shared.Severity.WARNING, () -> {
                throw new IllegalStateException("could not render the message");
            });
            finished[0] = true;
        })).doesNotThrowAnyException();

        assertThat(finished[0])
                .as("one unwritable message is not a reason to abandon the batch")
                .isTrue();
    }
}
