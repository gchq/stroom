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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.FormatPresenter.FormatView;
import stroom.query.api.Column;
import stroom.query.api.DateTimeFormatSettings;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.NumberFormatSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.client.presenter.TimeZones;
import stroom.widget.popup.client.presenter.PopupSize;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class TestFormatPresenter {

    private final EventBus eventBus = new SimpleEventBus();
    private FormatView view;
    private FormatPresenter presenter;

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
        view = Mockito.mock(FormatView.class);
        presenter = new FormatPresenter(eventBus, view, Mockito.mock(TimeZones.class));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void show_noFormatAfterANumberFormat() {
        // Regression test: one presenter serves all a table's columns, and a column with no format
        // was shown with the number settings of the last column shown
        show(column(new Format(Type.NUMBER, new NumberFormatSettings(2, true), null)));
        Mockito.clearInvocations(view);

        show(column(null));

        Mockito.verify(view).setDecimalPlaces(0);
        Mockito.verify(view).setUseSeparator(false);
    }

    @Test
    void show_noFormatAfterADateFormat() {
        // Regression test: as above, for the date and time settings
        show(column(new Format(
                Type.DATE_TIME,
                new DateTimeFormatSettings(false, "dd/MM/yyyy", UserTimeZone.utc()),
                null)));
        Mockito.clearInvocations(view);

        show(column(null));

        Mockito.verify(view).setUsePreferences(true);
        Mockito.verify(view).setPattern(null);
    }

    @Test
    void show_numberFormat() {
        show(column(new Format(Type.NUMBER, new NumberFormatSettings(2, true), null)));

        Mockito.verify(view).setDecimalPlaces(2);
        Mockito.verify(view).setUseSeparator(true);
        Mockito.verify(view).setType(Type.NUMBER);
    }

    private void show(final Column column) {
        // The dialog's size is worked out from the browser window's
        try (final MockedStatic<PopupSize> ignored = Mockito.mockStatic(PopupSize.class)) {
            presenter.show(column, (oldColumn, newColumn) -> {
            });
        }
    }

    private Column column(final Format format) {
        return Column.builder()
                .id("column-id")
                .name("Column")
                .format(format)
                .build();
    }
}
