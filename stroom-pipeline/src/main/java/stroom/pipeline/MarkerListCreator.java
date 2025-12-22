/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.pipeline.shared.FetchMarkerResult;
import stroom.util.shared.ElementId;
import stroom.util.shared.Expander;
import stroom.util.shared.Marker;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.StreamLocation;
import stroom.util.shared.Summary;

import org.apache.commons.lang3.ArrayUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarkerListCreator {

    public List<Marker> createFullList(final Reader reader, final Severity... expandedSeverities) throws IOException {
        final Map<Severity, Integer> totals = new HashMap<>();
        final Map<Severity, List<StoredError>> markers = new HashMap<>();

        final BufferedReader bufferedReader = new BufferedReader(reader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            addLine(line, totals, markers, expandedSeverities);
        }

        // Combine markers into a list.
        final List<Marker> markersList = new ArrayList<>();
        for (final Severity severity : Severity.SEVERITIES) {
            final Integer total = totals.get(severity);
            if (total != null) {
                final boolean expanded = ArrayUtils.contains(expandedSeverities, severity);
                int count = total;
                if (count > FetchMarkerResult.MAX_TOTAL_MARKERS) {
                    count = FetchMarkerResult.MAX_TOTAL_MARKERS;
                }

                final Expander expander = new Expander(0, expanded, false);
                final Summary summary = new Summary(severity, count, total, expander);
                markersList.add(summary);

                if (expanded) {
                    final List<StoredError> stored = markers.get(severity);
                    if (stored != null) {
                        markersList.addAll(stored);
                    }
                }
            }
        }

        return markersList;
    }

    private void addLine(final String line, final Map<Severity, Integer> totals,
                         final Map<Severity, List<StoredError>> markers, final Severity[] expandedSeverities) {
        final String ln = line.trim();
        if (!ln.isEmpty()) {
            boolean added = false;
            for (final Severity severity : Severity.SEVERITIES) {
                // Try and match the severity.
                if (ln.contains(severity.getDisplayValue() + ":")) {
                    added = true;

                    // Add to the total number of markers for this severity.
                    Integer count = totals.get(severity);
                    if (count == null) {
                        count = 1;
                    } else {
                        count = count + 1;
                    }
                    totals.put(severity, count);

                    // Add this marker to the list if we haven't already
                    // exceeded the maximum number of markers we want to gather
                    // for this type.
                    if (count <= FetchMarkerResult.MAX_TOTAL_MARKERS) {
                        // Only add a marker to the list if the current severity
                        // is expanded.
                        if (ArrayUtils.contains(expandedSeverities, severity)) {
                            addMarker(ln, severity, markers);
                        }
                    }

                    // We have matched the current line with this severity so
                    // don't bother trying to match against further severities.
                    break;
                }
            }

            // If we couldn't match the line against any known severity then
            // just add the line as a warning so we at least see it.
            if (!added) {
                addMarker(ln, Severity.WARNING, markers);
            }
        }
    }

    private void addMarker(final String line, final Severity severity, final Map<Severity, List<StoredError>> markers) {
        final List<StoredError> markerList = markers.computeIfAbsent(severity, k -> new ArrayList<>());
        if (markerList.size() < FetchMarkerResult.MAX_MARKERS) {
            int partIndex = -1;
            int lineNo = -1;
            int colNo = -1;
            ElementId elementId = null;
            String message = line;

            final int locatorStart = line.indexOf('[');
            if (locatorStart != -1) {
                final int locatorEnd = line.indexOf(']', locatorStart);
                if (locatorEnd != -1) {
                    final String locator = line.substring(locatorStart + 1, locatorEnd);
                    final int space = locator.indexOf(' ');
                    if (space == -1) {
                        elementId = new ElementId(locator);
                    } else {
                        elementId = new ElementId(locator.substring(space + 1));

                        final String[] parts = locator.substring(0, space).split(":");
                        if (parts.length == 2) {
                            lineNo = Integer.parseInt(parts[0]);
                            colNo = Integer.parseInt(parts[1]);
                        } else if (parts.length == 3) {
                            partIndex = Integer.parseInt(parts[0]) - 1;
                            lineNo = Integer.parseInt(parts[1]);
                            colNo = Integer.parseInt(parts[2]);
                        }
                    }

                    final int sevDelim = line.indexOf(':', locatorEnd);
                    if (sevDelim != -1) {
                        message = line.substring(sevDelim + 1).trim();
                    } else {
                        message = line.substring(locatorEnd + 1).trim();
                    }
                }
            }

            final StoredError storedError = new StoredError(severity, new StreamLocation(partIndex, lineNo, colNo),
                    elementId, message);
            markerList.add(storedError);
        }
    }
}
