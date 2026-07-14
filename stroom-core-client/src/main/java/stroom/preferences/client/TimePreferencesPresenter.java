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

package stroom.preferences.client;

import stroom.document.client.event.ChangeEvent;
import stroom.document.client.event.ChangeEvent.ChangeHandler;
import stroom.document.client.event.ChangeUiHandlers;
import stroom.document.client.event.HasChangeHandlers;
import stroom.preferences.client.TimePreferencesPresenter.TimePreferencesView;
import stroom.query.api.UserTimeZone;
import stroom.ui.config.shared.UserPreferences;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public final class TimePreferencesPresenter
        extends MyPresenterWidget<TimePreferencesView>
        implements ChangeUiHandlers, HasChangeHandlers {

    @Inject
    public TimePreferencesPresenter(
            final EventBus eventBus,
            final TimePreferencesView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        ChangeEvent.fire(this);
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

    @Override
    public HandlerRegistration addChangeHandler(final ChangeHandler handler) {
        return addHandlerToSource(ChangeEvent.getType(), handler);
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
