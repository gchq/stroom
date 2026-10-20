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

import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.StreamLocation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestErrorStreamMarkers {

    @Test
    void readsEachMarkersSeverityElementLocationAndMessage() {
        final ErrorStreamMarkers.Parsed parsed = ErrorStreamMarkers.parse("""
                [2:14 xsltFilter] ERROR: Unknown element 'Foo'
                [1:3:45 splitter] FATAL: Unexpected end of file
                [recordCount] INFO: Processed 10 records
                """, false);

        assertThat(parsed.unreadable()).isZero();
        assertThat(parsed.markers())
                .extracting(StoredError::getSeverity, marker -> marker.getElementId().getId(), StoredError::getMessage)
                .containsExactly(
                        tuple(Severity.ERROR, "xsltFilter", "Unknown element 'Foo'"),
                        tuple(Severity.FATAL_ERROR, "splitter", "Unexpected end of file"),
                        tuple(Severity.INFO, "recordCount", "Processed 10 records"));
        assertThat(parsed.markers().get(0).getLocation().getLineNo()).isEqualTo(2);
        assertThat(parsed.markers().get(0).getLocation().getColNo()).isEqualTo(14);
        assertThat(((StreamLocation) parsed.markers().get(1).getLocation()).getPartIndex()).isZero();
        assertThat(parsed.markers().get(1).getElementId()).isEqualTo(new ElementId("splitter"));
    }

    @Test
    void countsLinesThatArentMarkersWithoutLosingTheRest() {
        final ErrorStreamMarkers.Parsed parsed = ErrorStreamMarkers.parse("""
                not a marker at all
                [x:y xsltFilter] ERROR: a bad location

                [2:14 xsltFilter] WARN: Kept
                """, false);

        assertThat(parsed.unreadable()).isEqualTo(2);
        assertThat(parsed.markers()).extracting(StoredError::getMessage).containsExactly("Kept");
    }

    @Test
    void leavesOutTheLastLineOfTextCutShort() {
        final ErrorStreamMarkers.Parsed parsed = ErrorStreamMarkers.parse(
                "[2:14 xsltFilter] ERROR: Whole\n[3:1 xsltFilter] ERROR: Cut sh", true);

        assertThat(parsed.markers()).extracting(StoredError::getMessage).containsExactly("Whole");
        assertThat(parsed.unreadable()).isZero();
    }

    @Test
    void readsNothingFromNoText() {
        assertThat(ErrorStreamMarkers.parse("", false).markers()).isEmpty();
        assertThat(ErrorStreamMarkers.parse(null, false).markers()).isEmpty();
    }
}
