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

package stroom.pipeline;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestDefaultErrorWriter {

    @Mock
    private DestinationProvider destinationProvider;
    @Mock
    private Destination destination;

    private final DefaultErrorWriter errorWriter = new DefaultErrorWriter();
    private final List<Exception> failures = new ArrayList<>();

    @BeforeEach
    void beforeEach() {
        errorWriter.addOutputStreamProvider(destinationProvider);
        errorWriter.setFailureConsumer(failures::add);
    }

    @Test
    void destinationThatCannotBeBorrowedIsAFailure() throws IOException {
        final IOException e = new IOException("Expected");
        Mockito.when(destinationProvider.borrowDestination()).thenThrow(e);

        log();

        assertThat(failures).containsExactly(e);
    }

    @Test
    void ioExceptionIsAFailureAndTheDestinationIsReturned() throws IOException {
        final IOException e = new IOException("Expected");
        Mockito.when(destinationProvider.borrowDestination()).thenReturn(destination);
        Mockito.when(destination.getOutputStream()).thenThrow(e);

        log();

        assertThat(failures).containsExactly(e);
        Mockito.verify(destinationProvider).returnDestination(destination);
    }

    @Test
    void runtimeExceptionIsAFailureAndIsPassedOn() throws IOException {
        final RuntimeException e = new RuntimeException("Expected");
        Mockito.when(destinationProvider.borrowDestination()).thenReturn(destination);
        Mockito.when(destination.getOutputStream()).thenThrow(e);

        assertThatThrownBy(this::log).isSameAs(e);

        assertThat(failures).containsExactly(e);
    }

    @Test
    void failureWithNothingToTellIsIgnored() throws IOException {
        errorWriter.setFailureConsumer(null);
        Mockito.when(destinationProvider.borrowDestination()).thenThrow(new IOException("Expected"));

        log();

        assertThat(failures).isEmpty();
    }

    private void log() {
        errorWriter.log(Severity.ERROR, null, new ElementId("test"), "message");
    }
}
