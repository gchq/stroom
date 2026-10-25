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

package stroom.data.client.component;

import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestCharacterRangeSelectionController {

    private final List<String> problems = new ArrayList<>();
    private final List<Runnable> problemClosers = new ArrayList<>();
    private final List<DataRange> accepted = new ArrayList<>();

    @Test
    void anInvalidRangeLetsTheDialogBeUsedAgain() {
        // Regression test: the range's problem was shown with no callback and the dialog neither
        // closed nor reset, so it was left with OK and Cancel disabled. This covers what OK does
        // with a problem; that the controller passes the alert's close on to it is covered in the
        // browser by Widgets/Editors & Viewers/SourcePresenter InvalidRangeKeepsDialogUsable.
        final HidePopupRequestEvent okPressed = press(true, range(4, 4));

        // The offsets are shown one based, added rather than appended ("51")
        assertThat(problems).containsExactly("From offset 5 is equal to the to offset 5");
        Mockito.verify(okPressed, Mockito.never()).reset();
        problemClosers.getFirst().run();
        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        assertThat(accepted).isEmpty();
    }

    @Test
    void aRangeThatEndsBeforeItStartsSaysSo() {
        press(true, range(4, 2));

        assertThat(problems).containsExactly("To offset 3 is before from offset 5");
    }

    @Test
    void aValidRangeClosesTheDialog() {
        final DataRange range = range(0, 9);

        final HidePopupRequestEvent okPressed = press(true, range);

        assertThat(problems).isEmpty();
        Mockito.verify(okPressed).hide();
        assertThat(accepted).containsExactly(range);
    }

    @Test
    void cancelClosesTheDialogWhateverTheRange() {
        final HidePopupRequestEvent cancelPressed = press(false, range(4, 2));

        assertThat(problems).isEmpty();
        Mockito.verify(cancelPressed).hide();
        assertThat(accepted).isEmpty();
    }

    @Test
    void locations() {
        assertThat(CharacterRangeSelectionController.getDataRangeProblem(DataRange.builder()
                .fromLocation(DefaultLocation.of(3, 1))
                .toLocation(DefaultLocation.of(2, 5))
                .build()))
                .isEqualTo("To location 2:5 is before from location 3:1");
        assertThat(CharacterRangeSelectionController.getDataRangeProblem(DataRange.builder()
                .fromLocation(DefaultLocation.of(3, 1))
                .toLocation(DefaultLocation.of(3, 1))
                .build()))
                .isEqualTo("From location 3:1 is equal to the to location 3:1");
        assertThat(CharacterRangeSelectionController.getDataRangeProblem(DataRange.builder()
                .fromLocation(DefaultLocation.of(3, 1))
                .toLocation(DefaultLocation.of(4, 1))
                .build()))
                .isNull();
    }

    @Test
    void aRangeWithACountHasNoProblem() {
        assertThat(CharacterRangeSelectionController.getDataRangeProblem(DataRange.builder()
                .fromCharOffset(4L)
                .withLength(1L)
                .build()))
                .isNull();
    }

    // Presses OK or Cancel with the given range entered
    private HidePopupRequestEvent press(final boolean ok, final DataRange range) {
        final HidePopupRequestEvent event = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(event.isOk()).thenReturn(ok);
        CharacterRangeSelectionController.onHideRequest(event, range, (problem, onClose) -> {
            problems.add(problem);
            problemClosers.add(onClose);
        }, accepted::add);
        return event;
    }

    // A range of zero based character offsets
    private static DataRange range(final long from, final long to) {
        return DataRange.builder()
                .fromCharOffset(from)
                .toCharOffset(to)
                .build();
    }
}
