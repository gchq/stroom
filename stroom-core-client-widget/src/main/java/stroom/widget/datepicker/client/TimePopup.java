/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.widget.datepicker.client;

import stroom.util.shared.time.Time;
import stroom.widget.datepicker.client.DateTimePopup.DateTimeView;
import stroom.widget.datepicker.client.TimePopup.TimeView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class TimePopup extends MyPresenterWidget<TimeView> {

    @Inject
    public TimePopup(final EventBus eventBus,
                     final TimeView view) {
        super(eventBus, view);
    }

    public void show(final Consumer<Time> consumer) {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Set Time")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    // This method is overwritten so that we can validate the schedule
                    // before saving. Getting the scheduled times acts as validation.
                    if (e.isOk()) {
                        consumer.accept(getTime());
                        e.hide();
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    public Time getTime() {
        return getView().getTime();
    }

    public void setTime(final Time time) {
        getView().setTime(time);
    }

    public interface TimeView extends View, Focus {

        Time getTime();

        void setTime(Time time);

        void setHourVisible(boolean visible);

        void setMinuteVisible(boolean visible);

        void setSecondVisible(boolean visible);
    }
}
