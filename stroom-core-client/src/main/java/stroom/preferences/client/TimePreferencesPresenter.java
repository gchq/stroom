/*
 * Copyright 2023 Crown Copyright
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

package stroom.preferences.client;

import stroom.document.client.event.ChangeUiHandlers;
import stroom.preferences.client.TimePreferencesPresenter.TimePreferencesView;
import stroom.query.api.UserTimeZone;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public final class TimePreferencesPresenter
        extends MyPresenterWidget<TimePreferencesView>
        implements ChangeUiHandlers {

    private Runnable onChange = () -> {
    };

    @Inject
    public TimePreferencesPresenter(
            final EventBus eventBus,
            final TimePreferencesView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        onChange.run();
    }

    public void read(final UserPreferences userPreferences) {
        getView().setPattern(userPreferences.getDateTimePattern());
        final UserTimeZone timeZone = userPreferences.getTimeZone();
        if (timeZone != null) {
            getView().setTimeZoneUse(timeZone.getUse());
            getView().setTimeZoneId(timeZone.getId());
            getView().setTimeZoneOffsetHours(timeZone.getOffsetHours());
            getView().setTimeZoneOffsetMinutes(timeZone.getOffsetMinutes());
        }
    }

    public void write(final UserPreferences.Builder builder) {
        final UserTimeZone timeZone = UserTimeZone.builder()
                .use(getView().getTimeZoneUse())
                .id(getView().getTimeZoneId())
                .offsetHours(getView().getTimeZoneOffsetHours())
                .offsetMinutes(getView().getTimeZoneOffsetMinutes())
                .build();
        builder
                .dateTimePattern(getView().getPattern())
                .timeZone(timeZone);
    }

    /// @param onChange What to run when the user changes a time preference.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    public interface TimePreferencesView extends View, Focus, HasUiHandlers<ChangeUiHandlers> {

        String getPattern();

        void setPattern(String pattern);

        UserTimeZone.Use getTimeZoneUse();

        void setTimeZoneUse(UserTimeZone.Use use);

        String getTimeZoneId();

        void setTimeZoneId(String timeZoneId);

        Integer getTimeZoneOffsetHours();

        void setTimeZoneOffsetHours(Integer timeZoneOffsetHours);

        Integer getTimeZoneOffsetMinutes();

        void setTimeZoneOffsetMinutes(Integer timeZoneOffsetMinutes);
    }
}
