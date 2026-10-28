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

import stroom.alert.client.event.AlertEvent;
import stroom.util.shared.Count;
import stroom.util.shared.DataRange;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// Shows the "Set Source Range" dialog ([CharacterRangeSelection]) for one use. OK checks the range:
/// one that ends before it starts, or where it starts, is reported and the dialog stays open.
public class CharacterRangeSelectionController extends Controller {

    private final CharacterRangeSelection view;

    /// Made by GIN for one use, with the dialog's content.
    @Inject
    public CharacterRangeSelectionController(final EventBus eventBus, final CharacterRangeSelection view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK gives the range chosen to `onOk`
    /// if it is valid.
    ///
    /// @param opener          What shows it.
    /// @param dataRange       The range to start from, zero based.
    /// @param totalCharsCount The number of characters in the source.
    /// @param onOk            Given the range chosen, zero based.
    public void edit(final HasControllerNode opener,
                     final DataRange dataRange,
                     final Count<Long> totalCharsCount,
                     final Consumer<DataRange> onOk) {
        view.setDataRange(dataRange);
        view.setTotalCharsCount(totalCharsCount);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Set Source Range")
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e,
                        view.getDataRange(),
                        (problem, onClose) -> AlertEvent.fireError(this, problem, onClose::run),
                        onOk))
                .fire();
    }

    /// What OK and Cancel do: OK with a range that has a problem reports it (letting the user
    /// correct it or cancel once the report is closed); OK with a valid one gives it to `onOk` and
    /// closes the dialog; Cancel closes it.
    ///
    /// @param event     The request to close the dialog.
    /// @param dataRange The range entered.
    /// @param onProblem Given the problem to report, and what to do once it is closed.
    /// @param onOk      Given a valid range on OK.
    static void onHideRequest(final HidePopupRequestEvent event,
                              final DataRange dataRange,
                              final BiConsumer<String, Runnable> onProblem,
                              final Consumer<DataRange> onOk) {
        final String problem = event.isOk()
                ? getDataRangeProblem(dataRange)
                : null;
        if (problem != null) {
            onProblem.accept(problem, event::reset);
        } else {
            if (event.isOk()) {
                onOk.accept(dataRange);
            }
            event.hide();
        }
    }

    /// @param dataRange A range, zero based.
    /// @return What is wrong with it, with its offsets one based, as the user sees them, or null if
    ///         nothing is.
    static String getDataRangeProblem(final DataRange dataRange) {
        if (dataRange.getLocationFrom() != null
            && dataRange.getLocationTo() != null
            && dataRange.getLocationTo().isBefore(dataRange.getLocationFrom())) {
            return "To location " + dataRange.getLocationTo()
                   + " is before from location " + dataRange.getLocationFrom();
        } else if (dataRange.getLocationFrom() != null
                   && dataRange.getLocationTo() != null
                   && dataRange.getLocationTo().equals(dataRange.getLocationFrom())) {
            return "From location " + dataRange.getLocationFrom()
                   + " is equal to the to location " + dataRange.getLocationTo();
        } else if (dataRange.getCharOffsetFrom() != null
                   && dataRange.getCharOffsetTo() != null
                   && dataRange.getCharOffsetTo() < dataRange.getCharOffsetFrom()) {
            return "To offset " + (dataRange.getCharOffsetTo() + 1)
                   + " is before from offset " + (dataRange.getCharOffsetFrom() + 1);
        } else if (dataRange.getCharOffsetFrom() != null
                   && dataRange.getCharOffsetTo() != null
                   && dataRange.getCharOffsetTo().equals(dataRange.getCharOffsetFrom())) {
            return "From offset " + (dataRange.getCharOffsetFrom() + 1)
                   + " is equal to the to offset " + (dataRange.getCharOffsetTo() + 1);
        }
        return null;
    }
}
