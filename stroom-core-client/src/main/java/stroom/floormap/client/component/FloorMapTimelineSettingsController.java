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

package stroom.floormap.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.Rect;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The timeline settings popup ([FloorMapTimelineSettings]), shown when the timeline's settings
/// button is pressed: loop playback and the date range, which it keeps between showings, so the
/// timeline that owns it reads them whenever it needs them.
public class FloorMapTimelineSettingsController extends Controller {

    private final FloorMapTimelineSettings view;

    /// Made by GIN, with the popup's content.
    ///
    /// @param eventBus                      The application's event bus, which the popup is shown on.
    /// @param view                          The popup's content.
    /// @param dateTimeBoxControllerProvider Makes what opens each date-time box's dialog.
    @Inject
    public FloorMapTimelineSettingsController(final EventBus eventBus,
                                              final FloorMapTimelineSettings view,
                                              final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        make(dateTimeBoxControllerProvider).attach(view.getStartBox());
        make(dateTimeBoxControllerProvider).attach(view.getEndBox());
    }

    /// Shows the popup above an anchor, e.g. the settings button; shown already, it closes.
    ///
    /// @param anchor The widget it is shown above.
    public void show(final Widget anchor) {
        final Rect relativeRect = new Rect(anchor.getElement()).grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.ABOVE);
        ControllerDialog.show(this)
                .popupType(PopupType.POPUP)
                .caption("Timeline Settings")
                .popupPosition(popupPosition)
                .addAutoHidePartner(anchor.getElement())
                .fire();
    }

    /// @return Whether loop playback is on.
    public boolean isLoopPlayback() {
        return view.isLoopPlayback();
    }

    /// @param loop Whether playback loops, rather than stopping at the end.
    public void setLoopPlayback(final boolean loop) {
        view.setLoopPlayback(loop);
    }

    /// @param startTime The start of the range shown, in milliseconds.
    public void setStartTime(final long startTime) {
        view.setStartTime(startTime);
    }

    /// @param endTime The end of the range shown, in milliseconds.
    public void setEndTime(final long endTime) {
        view.setEndTime(endTime);
    }

    /// @return The start of the range, in milliseconds.
    public long getStartTime() {
        return view.getStartTime();
    }

    /// @return The end of the range, in milliseconds.
    public long getEndTime() {
        return view.getEndTime();
    }

    /// @param onStartChange Run when the user changes the start of the range.
    public void setOnStartChange(final Runnable onStartChange) {
        view.setOnStartChange(onStartChange);
    }

    /// @param onEndChange Run when the user changes the end of the range.
    public void setOnEndChange(final Runnable onEndChange) {
        view.setOnEndChange(onEndChange);
    }

    /// Sets what "Show All" does: work out the whole data range and set it with
    /// [#setStartTime(long)] and [#setEndTime(long)].
    ///
    /// @param onShowAll Run when it is pressed.
    public void setOnShowAll(final Runnable onShowAll) {
        view.setOnShowAll(onShowAll);
    }

    /// @param enabled Whether "Show All" can be pressed: not until at least one histogram data
    ///                point has been received.
    public void setShowAllEnabled(final boolean enabled) {
        view.setShowAllEnabled(enabled);
    }
}
