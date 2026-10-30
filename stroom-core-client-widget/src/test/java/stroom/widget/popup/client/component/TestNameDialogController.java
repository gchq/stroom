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

package stroom.widget.popup.client.component;

import stroom.widget.popup.client.event.HidePopupRequestEvent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The dialog's OK and Cancel handling; the dialog itself (shown, typed in) is checked by its
// workbench stories (Widgets/Dialogs/NameDialog)
class TestNameDialogController {

    private final List<String> invalid = new ArrayList<>();
    private final List<String> accepted = new ArrayList<>();
    private int validCount;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void noName(final String name) {
        // Regression test: OK with no name did nothing and said nothing (e.g. Save Tab Session)
        assertThat(NameDialogController.problemWith(name))
                .isEqualTo(NameDialogController.NO_NAME_MESSAGE);
    }

    @Test
    void name() {
        assertThat(NameDialogController.problemWith("My session"))
                .isNull();
    }

    @Test
    void nameWithSpacesAround() {
        // Accepted as typed: a name with spaces around it is still a name
        assertThat(NameDialogController.problemWith(" My session "))
                .isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void ok_noName(final String name) {
        final HidePopupRequestEvent okPressed = request(true);

        onHideRequest(okPressed, name);

        assertThat(invalid)
                .containsExactly(NameDialogController.NO_NAME_MESSAGE);
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        assertThat(accepted)
                .isEmpty();
    }

    @Test
    void ok_name() {
        final HidePopupRequestEvent okPressed = request(true);

        onHideRequest(okPressed, "My session");

        assertThat(accepted)
                .containsExactly("My session");
        assertThat(validCount)
                .isEqualTo(1);
        Mockito.verify(okPressed).hide();
        assertThat(invalid)
                .isEmpty();
    }

    @Test
    void ok_nameAfterNoName() {
        // Once a name is given, the message is cleared and the name accepted
        final HidePopupRequestEvent okPressed = request(true);
        final HidePopupRequestEvent okPressedAgain = request(true);

        onHideRequest(okPressed, "");
        onHideRequest(okPressedAgain, "My session");

        assertThat(invalid)
                .containsExactly(NameDialogController.NO_NAME_MESSAGE);
        assertThat(validCount)
                .isEqualTo(1);
        assertThat(accepted)
                .containsExactly("My session");
        Mockito.verify(okPressed, Mockito.never()).hide();
        Mockito.verify(okPressedAgain).hide();
    }

    @Test
    void cancel() {
        // Closes whatever is typed, giving nothing
        final HidePopupRequestEvent cancelPressed = request(false);

        onHideRequest(cancelPressed, "My session");

        Mockito.verify(cancelPressed).hide();
        assertThat(accepted)
                .isEmpty();
        assertThat(invalid)
                .isEmpty();
    }

    @Test
    void cancel_noName() {
        // Cancel with no name doesn't complain about it
        final HidePopupRequestEvent cancelPressed = request(false);

        onHideRequest(cancelPressed, "");

        Mockito.verify(cancelPressed).hide();
        Mockito.verify(cancelPressed, Mockito.never()).reset();
        assertThat(invalid)
                .isEmpty();
    }

    private HidePopupRequestEvent request(final boolean ok) {
        final HidePopupRequestEvent event = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(event.isOk()).thenReturn(ok);
        return event;
    }

    private void onHideRequest(final HidePopupRequestEvent event, final String name) {
        NameDialogController.onHideRequest(event, name, invalid::add, () -> validCount++, accepted::add);
    }
}
