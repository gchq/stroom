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

package stroom.floormap.client.presenter;

import stroom.floormap.client.presenter.FloorMapTimelineSettingsPresenter.FloorMapTimelineSettingsView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.Rect;

import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

/// Presenter for the timeline settings popup.
/// Shows loop-playback and date range controls when the settings button is clicked.
/// Playback speed has its own menu opened from the speed badge on the timeline.
public class FloorMapTimelineSettingsPresenter
        extends MyPresenterWidget<FloorMapTimelineSettingsView> {

    @Inject
    public FloorMapTimelineSettingsPresenter(final EventBus eventBus,
                                             final FloorMapTimelineSettingsView view) {
        super(eventBus, view);
    }

    /// Show the settings popup positioned relative to the anchor widget (e.g. the settings button).
    ///
    /// @param anchor The widget to anchor the popup above/below.
    public void show(final Widget anchor) {
        Rect relativeRect = new Rect(anchor.getElement());
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.ABOVE);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.POPUP)
                .popupPosition(popupPosition)
                .addAutoHidePartner(anchor.getElement())
                .fire();
    }

    /// Hides the settings popup.
    public void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }

    /// Returns `true` if loop playback is enabled.
    public boolean isLoopPlayback() {
        return getView().isLoopPlayback();
    }

    /// Enables or disables loop playback mode.
    ///
    /// @param loop `true` to loop, `false` to stop at end
    public void setLoopPlayback(final boolean loop) {
        getView().setLoopPlayback(loop);
    }

    /// Sets the start time displayed in the date picker.
    ///
    /// @param startTime start time in milliseconds
    public void setStartTime(final long startTime) {
        getView().setStartTime(startTime);
    }

    /// Sets the end time displayed in the date picker.
    ///
    /// @param endTime end time in milliseconds
    public void setEndTime(final long endTime) {
        getView().setEndTime(endTime);
    }

    /// Returns the start time from the date picker, in milliseconds.
    public long getStartTime() {
        return getView().getStartTime();
    }

    /// Returns the end time from the date picker, in milliseconds.
    public long getEndTime() {
        return getView().getEndTime();
    }

    /// Registers a handler that fires when the start time date picker value changes.
    ///
    /// @param handler the value-change handler
    /// @return the handler registration for later removal
    public HandlerRegistration addStartTimeChangeHandler(final ValueChangeHandler<String> handler) {
        return getView().addStartTimeChangeHandler(handler);
    }

    /// Registers a handler that fires when the end time date picker value changes.
    ///
    /// @param handler the value-change handler
    /// @return the handler registration for later removal
    public HandlerRegistration addEndTimeChangeHandler(final ValueChangeHandler<String> handler) {
        return getView().addEndTimeChangeHandler(handler);
    }

    /// Registers the handler called when the user clicks the "Show All" button.
    /// The handler should respond by computing the full data range and calling
    /// [#setStartTime(long)] / [#setEndTime(long)] to update the date pickers.
    public void setShowAllHandler(final Runnable handler) {
        getView().setShowAllHandler(handler);
    }

    /// Enables or disables the "Show All" button.
    /// Should be disabled until at least one histogram data point has been received.
    public void setShowAllEnabled(final boolean enabled) {
        getView().setShowAllEnabled(enabled);
    }

    public interface FloorMapTimelineSettingsView extends View {

        boolean isLoopPlayback();

        void setLoopPlayback(boolean loop);

        void setStartTime(long startTime);

        void setEndTime(long endTime);

        long getStartTime();

        long getEndTime();

        HandlerRegistration addStartTimeChangeHandler(ValueChangeHandler<String> handler);

        HandlerRegistration addEndTimeChangeHandler(ValueChangeHandler<String> handler);

        void setShowAllHandler(Runnable handler);

        void setShowAllEnabled(boolean enabled);
    }
}
