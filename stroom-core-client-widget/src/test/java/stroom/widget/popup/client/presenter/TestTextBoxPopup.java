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

package stroom.widget.popup.client.presenter;

import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.TextBoxPopup.TextBoxView;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextBoxPopup {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ShowPopupEvent> shown = new ArrayList<>();
    private final List<String> accepted = new ArrayList<>();
    private TextBoxView view;
    private TextBoxPopup popup;

    @BeforeEach
    void setUp() {
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);
        view = Mockito.mock(TextBoxView.class);
        popup = new TextBoxPopup(eventBus, view);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void ok_noName(final String name) {
        // Regression test: OK with no name did nothing and said nothing (e.g. Save Tab Session)
        Mockito.when(view.getText()).thenReturn(name);

        final HidePopupRequestEvent okPressed = showAndPressOk();

        Mockito.verify(view).setInvalid(TextBoxPopup.NO_NAME_MESSAGE);
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        assertThat(accepted)
                .isEmpty();
    }

    @Test
    void ok_name() {
        Mockito.when(view.getText()).thenReturn("My session");

        final HidePopupRequestEvent okPressed = showAndPressOk();

        assertThat(accepted)
                .containsExactly("My session");
        Mockito.verify(okPressed).hide();
        Mockito.verify(view, Mockito.never()).setInvalid(Mockito.anyString());
    }

    @Test
    void ok_nameAfterNoName() {
        // Once a name is given, the message is cleared and the name accepted
        Mockito.when(view.getText()).thenReturn("", "My session");

        showAndPressOk();
        final HidePopupRequestEvent okPressedAgain = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressedAgain.isOk()).thenReturn(true);
        shown.getFirst().getHideRequestHandler().onHideRequest(okPressedAgain);

        assertThat(accepted)
                .containsExactly("My session");
        Mockito.verify(okPressedAgain).hide();
        // Cleared when shown, and again when the name was accepted
        Mockito.verify(view, Mockito.times(2)).setValid();
    }

    @Test
    void cancel() {
        showPopup();
        final HidePopupRequestEvent cancelPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(cancelPressed.isOk()).thenReturn(false);

        shown.getFirst().getHideRequestHandler().onHideRequest(cancelPressed);

        Mockito.verify(cancelPressed).hide();
        assertThat(accepted)
                .isEmpty();
    }

    private void showPopup() {
        // The popup's size is worked out from the browser window's
        try (final MockedStatic<PopupSize> ignored = Mockito.mockStatic(PopupSize.class)) {
            popup.show("Save New Tab Session", accepted::add);
        }
        assertThat(shown)
                .hasSize(1);
    }

    private HidePopupRequestEvent showAndPressOk() {
        showPopup();
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        shown.getFirst().getHideRequestHandler().onHideRequest(okPressed);
        return okPressed;
    }
}
