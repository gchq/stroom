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

import stroom.pipeline.MarkerListCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Marker;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/// Reads the markers (errors, warnings and so on) from the text of an Error stream, as the data viewer does, with
/// [MarkerListCreator]. A pipeline writes one marker per line, e.g.
/// `[2:14 xsltFilter] ERROR: Unknown element 'Foo'`: where in its input it was (optionally the part, then the
/// line and column), the pipeline element, the severity and the message.
///
/// Each line is read on its own, so one line that can't be read is counted and left out rather than losing the
/// rest of the stream.
final class ErrorStreamMarkers {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ErrorStreamMarkers.class);

    private ErrorStreamMarkers() {
        // Static methods only.
    }

    /// @param text      The text of an Error stream, or the start of it.
    /// @param truncated True if the text was cut short, so its last line may be incomplete and is left out.
    /// @return The markers read, in order, and how many lines couldn't be read.
    static Parsed parse(final String text, final boolean truncated) {
        final List<StoredError> markers = new ArrayList<>();
        int unreadable = 0;
        if (text == null || text.isEmpty()) {
            return new Parsed(markers, unreadable);
        }
        final List<String> lines = new ArrayList<>(text.lines().toList());
        if (truncated && !lines.isEmpty() && !text.endsWith("\n")) {
            lines.removeLast();
        }
        final MarkerListCreator creator = new MarkerListCreator();
        for (final String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                final List<Marker> parsed = creator.createFullList(new StringReader(line), Severity.SEVERITIES);
                boolean found = false;
                for (final Marker marker : parsed) {
                    if (marker instanceof final StoredError storedError) {
                        markers.add(storedError);
                        found = true;
                    }
                }
                if (!found) {
                    unreadable++;
                }
            } catch (final IOException | RuntimeException e) {
                LOGGER.debug(() -> "Unable to read the marker: " + line, e);
                unreadable++;
            }
        }
        return new Parsed(markers, unreadable);
    }


    // --------------------------------------------------------------------------------


    /// The markers read from an Error stream.
    ///
    /// @param markers    The markers, in the order they were written.
    /// @param unreadable How many lines couldn't be read as markers.
    record Parsed(List<StoredError> markers, int unreadable) {

    }
}
