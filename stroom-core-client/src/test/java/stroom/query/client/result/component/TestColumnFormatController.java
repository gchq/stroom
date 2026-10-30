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
}
