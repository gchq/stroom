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

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.sample.PartSampler;

import org.mockito.Mockito;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.function.IntFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

/// A mocked stream reader's part samples, read from each part's text by the real sampler, as
/// [StreamReader#samplePart] reads them from the stream store.
final class SampledParts {

    private SampledParts() {
        // Static only.
    }

    /// Has the reader sample each part of a stream from its text.
    ///
    /// @param reader   The mocked reader.
    /// @param streamId The stream.
    /// @param partText Each part's text, by its zero-based index.
    static void stub(final StreamReader reader, final long streamId, final IntFunction<String> partText) {
        Mockito.when(reader.samplePart(eq(streamId), anyInt(), any())).thenAnswer(invocation ->
                sample(partText.apply(invocation.getArgument(1)), invocation.getArgument(2)));
    }

    /// @param text   A part's text.
    /// @param limits How much is kept.
    /// @return Its sample, as the stream reader reads it.
    static PartSampler.Sample sample(final String text, final PartSampler.Limits limits) {
        try {
            return PartSampler.sample(() -> new StringReader(text), limits, () -> false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
