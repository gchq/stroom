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

package stroom.query.client.result.component;

import stroom.query.api.DateTimeFormatSettings;
import stroom.query.api.DurationFormatSettings;
import stroom.query.api.DurationFormatSettings.Style;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.NumberFormatSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.client.TimeZones;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestColumnFormatController {

    private final EventBus eventBus = new SimpleEventBus();
    private ColumnFormat view;
    private ColumnFormatController controller;

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
        view = Mockito.mock(ColumnFormat.class);
        controller = new ColumnFormatController(eventBus, view, Mockito.mock(TimeZones.class));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void read_noFormatAfterANumberFormat() {
        // Regression test: one controller served all a table's columns, and a column with no format
        // was shown with the number settings of the last column shown
        controller.read(new Format(Type.NUMBER, new NumberFormatSettings(2, true), null));
        Mockito.clearInvocations(view);

        controller.read(null);

        Mockito.verify(view).setDecimalPlaces(0);
        Mockito.verify(view).setUseSeparator(false);
    }

    @Test
    void read_noFormatAfterADateFormat() {
        // Regression test: as above, for the date and time settings
        controller.read(new Format(
                Type.DATE_TIME,
                new DateTimeFormatSettings(false, "dd/MM/yyyy", UserTimeZone.utc()),
                null));
        Mockito.clearInvocations(view);

        controller.read(null);

        Mockito.verify(view).setUsePreferences(true);
        Mockito.verify(view).setPattern(null);
    }

    @Test
    void read_numberFormat() {
        controller.read(new Format(Type.NUMBER, new NumberFormatSettings(2, true), null));

        Mockito.verify(view).setDecimalPlaces(2);
        Mockito.verify(view).setUseSeparator(true);
        Mockito.verify(view).setType(Type.NUMBER);
    }

    @Test
    void read_durationFormat() {
        controller.read(new Format(
                Type.DURATION, new DurationFormatSettings(Style.PATTERN, "hh:mm"), null));

        Mockito.verify(view).setDurationStyle(Style.PATTERN);
        Mockito.verify(view).setDurationPattern("hh:mm");
        Mockito.verify(view).setType(Type.DURATION);
    }

    @Test
    void read_noFormatAfterADurationFormat() {
        controller.read(new Format(
                Type.DURATION, new DurationFormatSettings(Style.PATTERN, "hh:mm"), null));
        Mockito.clearInvocations(view);

        controller.read(null);

        Mockito.verify(view).setDurationStyle(Style.EXACT);
        Mockito.verify(view).setDurationPattern(null);
    }

    @Test
    void write_durationFormat() {
        Mockito.when(view.getType()).thenReturn(Type.DURATION);
        Mockito.when(view.getDurationStyle()).thenReturn(Style.PATTERN);
        Mockito.when(view.getDurationPattern()).thenReturn("hh:mm");

        assertThat(controller.write()).isEqualTo(new Format(
                Type.DURATION, new DurationFormatSettings(Style.PATTERN, "hh:mm"), null));
    }

    @Test
    void write_durationFormatWithNoPattern() {
        Mockito.when(view.getType()).thenReturn(Type.DURATION);
        Mockito.when(view.getDurationStyle()).thenReturn(Style.ISO);
        Mockito.when(view.getDurationPattern()).thenReturn("hh:mm");

        assertThat(controller.write()).isEqualTo(new Format(
                Type.DURATION, new DurationFormatSettings(Style.ISO, null), null));
    }

    @Test
    void durationPatternsSuggestedCanBeUsed() {
        assertThat(ColumnFormat.STANDARD_DURATION_PATTERNS)
                .allSatisfy(pattern -> assertThat(ColumnFormat.getInvalidDurationPatternReason(pattern)).isNull());
        assertThat(ColumnFormat.getInvalidDurationPatternReason("HH:mm")).contains("'H' that is not a unit");
    }
}
