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

package stroom.processor.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.processor.client.presenter.ProfilePeriodEditPresenter.ProfilePeriodEditView;
import stroom.processor.shared.ProfilePeriod;
import stroom.util.shared.time.Days;
import stroom.widget.datepicker.client.TimeBox;
import stroom.widget.datepicker.client.TimePopup;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class ProfilePeriodEditPresenter
        extends MyPresenterWidget<ProfilePeriodEditView> {

    private ProfilePeriod profilePeriod;

    @Inject
    public ProfilePeriodEditPresenter(final EventBus eventBus,
                                      final ProfilePeriodEditView view,
                                      final Provider<TimePopup> timePopupProvider) {
        super(eventBus, view);
        view.getStartTime().setPopupProvider(timePopupProvider);
        view.getEndTime().setPopupProvider(timePopupProvider);
    }

    public void show(final ProfilePeriod executionSchedule,
                     final Consumer<ProfilePeriod> consumer) {
        read(executionSchedule);

        final Size width = Size.builder().max(1000).resizable(true).build();
        final Size height = Size.builder().build();
        final PopupSize popupSize = PopupSize.builder().width(width).height(height).build();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit Period")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write(written -> {
                            consumer.accept(written);
                            e.hide();
                        }, e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    public void read(final ProfilePeriod profilePeriod) {
        this.profilePeriod = profilePeriod;
        getView().setDays(profilePeriod.getDays());
        getView().getStartTime().setValue(profilePeriod.getStartTime());
        getView().getEndTime().setValue(profilePeriod.getEndTime());
        getView().setLimitNodeThreads(profilePeriod.isLimitNodeThreads());
        getView().setMaxNodeThreads(profilePeriod.getMaxNodeThreads());
        getView().setLimitClusterThreads(profilePeriod.isLimitClusterThreads());
        getView().setMaxClusterThreads(profilePeriod.getMaxClusterThreads());
    }

    public void write(final Consumer<ProfilePeriod> consumer,
                      final HidePopupRequestEvent event) {
        if (!getView().getStartTime().isValid()) {
            AlertEvent.fireWarn(this, "Invalid start time", event::reset);
        } else if (!getView().getEndTime().isValid()) {
            AlertEvent.fireWarn(this, "Invalid end time", event::reset);
        } else {
            final ProfilePeriod schedule = profilePeriod
                    .copy()
                    .days(getView().getDays())
                    .startTime(getView().getStartTime().getValue())
                    .endTime(getView().getEndTime().getValue())
                    .limitNodeThreads(getView().isLimitNodeThreads())
                    .maxNodeThreads(getView().getMaxNodeThreads())
                    .limitClusterThreads(getView().isLimitClusterThreads())
                    .maxClusterThreads(getView().getMaxClusterThreads())
                    .build();
            consumer.accept(schedule);
        }
    }

    public interface ProfilePeriodEditView extends View, Focus {

        Days getDays();

        void setDays(Days days);

        TimeBox getStartTime();

        TimeBox getEndTime();

        boolean isLimitNodeThreads();

        void setLimitNodeThreads(boolean limitNodeThreads);

        int getMaxNodeThreads();

        void setMaxNodeThreads(int maxNodeThreads);

        boolean isLimitClusterThreads();

        void setLimitClusterThreads(boolean limitClusterThreads);

        int getMaxClusterThreads();

        void setMaxClusterThreads(int maxClusterThreads);
    }
}
