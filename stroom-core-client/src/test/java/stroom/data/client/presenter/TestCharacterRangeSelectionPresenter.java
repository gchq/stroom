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

package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.CharacterRangeSelectionPresenter.CharacterRangeSelectionView;
import stroom.util.shared.DataRange;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestCharacterRangeSelectionPresenter {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ShowPopupEvent> shown = new ArrayList<>();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private final List<DataRange> accepted = new ArrayList<>();
    private CharacterRangeSelectionView view;

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        view = Mockito.mock(CharacterRangeSelectionView.class);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void anInvalidRangeLetsTheDialogBeUsedAgain() {
        // Regression test: the range's problem was shown with no callback and the dialog neither
        // closed nor reset, so it was left with OK and Cancel disabled.
        final HidePopupRequestEvent okPressed = showAndPressOk(range(4, 4));

        assertThat(alerts)
                .hasSize(1);
        // The offsets are shown one based, added rather than appended ("51")
        assertThat(alerts.getFirst().getMessage().asString())
                .isEqualTo("From offset 5 is equal to the to offset 5");
        Mockito.verify(okPressed, Mockito.never()).reset();
        alerts.getFirst().getCallback().onClose();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        assertThat(accepted)
                .isEmpty();
    }

    @Test
    void aRangeThatEndsBeforeItStartsSaysSo() {
        showAndPressOk(range(4, 2));

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("To offset 3 is before from offset 5");
    }

    @Test
    void aValidRangeClosesTheDialog() {
        final DataRange range = range(0, 9);

        final HidePopupRequestEvent okPressed = showAndPressOk(range);

        assertThat(alerts)
                .isEmpty();
        Mockito.verify(okPressed).hide();
        assertThat(accepted)
                .containsExactly(range);
    }

    // Shows the dialog, with the given range entered, and presses OK.
    private HidePopupRequestEvent showAndPressOk(final DataRange range) {
        Mockito.when(view.getDataRange()).thenReturn(range);
        final CharacterRangeSelectionPresenter presenter = new CharacterRangeSelectionPresenter(eventBus, view);
        presenter.show(accepted::add);
        assertThat(shown)
                .hasSize(1);

        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        shown.getFirst().getHideRequestHandler().onHideRequest(okPressed);
        return okPressed;
    }

    // A range of zero based character offsets
    private static DataRange range(final long from, final long to) {
        return DataRange.builder()
                .fromCharOffset(from)
                .toCharOffset(to)
                .build();
    }
}
