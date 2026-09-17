/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.client.table;

import stroom.query.api.Column;
import stroom.query.api.DateTimeFormatSettings;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.FormatSettings;
import stroom.query.api.NumberFormatSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.client.presenter.TimeZones;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class FormatPresenter extends MyPresenterWidget<FormatPresenter.FormatView> implements FormatUihandlers {

    /**
     * What a column with no date/time settings shows. Matches {@link DateTimeFormatSettings.Builder}'s
     * own default, so opening a column that has never been formatted shows the value it would save.
     */
    private static final boolean DEFAULT_USE_PREFERENCES = true;

    /**
     * What a column with no number settings shows, and the fallback when it has them but they are
     * null — {@link NumberFormatSettings}'s {@code @JsonCreator} assigns both fields raw, so a
     * deserialised instance can hold nulls where the view takes primitives. Values match that class's
     * own defaults.
     */
    private static final int DEFAULT_DECIMAL_PLACES = 0;
    private static final boolean DEFAULT_USE_SEPARATOR = false;

    private final TimeZones timeZones;
    private Type type;

    @Inject
    public FormatPresenter(final EventBus eventBus, final FormatView view, final TimeZones timeZones) {
        super(eventBus, view);
        this.timeZones = timeZones;

        view.setUiHandlers(this);
        view.setTypes(Format.TYPES);
        timeZones.getIds(ids -> getView().setTimeZoneIds(ids));
    }

    public void show(final Column column,
                     final BiConsumer<Column, Column> columnChangeConsumer) {

        final Format format = column.getFormat();
        if (format == null || format.getType() == null) {
            setType(Type.GENERAL);
        } else {
            setNumberSettings(format.getSettings());
            setDateTimeSettings(format.getSettings());
            setType(format.getType());
        }

        getView().setWrap(format != null && format.getWrap() != null && format.getWrap());

        final PopupSize popupSize = PopupSize.resizable(450, 420);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Format '" + column.getName() + "'")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final Format newFormat = getFormat();
                        if (!Objects.equals(newFormat, column.getFormat())) {
                            columnChangeConsumer.accept(column, column.copy().format(newFormat).build());
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    @Override
    public void onTypeChange(final Type type) {
        setType(type);
    }

    private void setType(final Type type) {
        this.type = type;
        getView().setType(type);
    }

    private Format getFormat() {
        FormatSettings settings = null;
        if (Type.NUMBER.equals(type)) {
            settings = getNumberSettings();
        } else if (Type.DATE_TIME.equals(type)) {
            settings = getDateTimeSettings();
        }
        Boolean wrap = null;
        if (getView().isWrap()) {
            wrap = true;
        }

        return new Format(type, settings, wrap);
    }

    private FormatSettings getNumberSettings() {
        return new NumberFormatSettings(getView().getDecimalPlaces(), getView().isUseSeparator());
    }

    private void setNumberSettings(final FormatSettings settings) {
        if (settings instanceof final NumberFormatSettings numberFormatSettings) {
            getView().setDecimalPlaces(Objects.requireNonNullElse(
                    numberFormatSettings.getDecimalPlaces(), DEFAULT_DECIMAL_PLACES));
            getView().setUseSeparator(Objects.requireNonNullElse(
                    numberFormatSettings.getUseSeparator(), DEFAULT_USE_SEPARATOR));
        } else {
            getView().setDecimalPlaces(DEFAULT_DECIMAL_PLACES);
            getView().setUseSeparator(DEFAULT_USE_SEPARATOR);
        }
    }

    private FormatSettings getDateTimeSettings() {
        return new DateTimeFormatSettings(getView().isUsePreferences(), getView().getPattern(), getTimeZone());
    }

    private void setDateTimeSettings(final FormatSettings settings) {
        UserTimeZone timeZone = UserTimeZone.utc();

        if (settings instanceof final DateTimeFormatSettings dateTimeFormatSettings) {
            getView().setUsePreferences(dateTimeFormatSettings.isUsePreferences());
            getView().setPattern(dateTimeFormatSettings.getPattern());
            if (dateTimeFormatSettings.getTimeZone() != null) {
                timeZone = dateTimeFormatSettings.getTimeZone();
            }
        } else {
            getView().setUsePreferences(DEFAULT_USE_PREFERENCES);
            getView().setPattern(null);
        }

        setTimeZone(timeZone);
    }

    private UserTimeZone getTimeZone() {
        return new UserTimeZone(
                getView().getTimeZoneUse(),
                getView().getTimeZoneId(),
                getView().getTimeZoneOffsetHours(),
                getView().getTimeZoneOffsetMinutes());
    }

    private void setTimeZone(final UserTimeZone timeZone) {
        getView().setTimeZoneUse(timeZone.getUse());

        if (timeZone.getId() == null) {
            getView().setTimeZoneId(timeZones.getLocalTimeZoneId());
        } else {
            getView().setTimeZoneId(timeZone.getId());
        }

        getView().setTimeZoneOffsetHours(timeZone.getOffsetHours());
        getView().setTimeZoneOffsetMinutes(timeZone.getOffsetMinutes());
    }

    public interface FormatView extends View, Focus, HasUiHandlers<FormatUihandlers> {

        void setTypes(List<Type> types);

        void setType(Type type);

        int getDecimalPlaces();

        void setDecimalPlaces(int decimalPlaces);

        boolean isUseSeparator();

        void setUseSeparator(boolean useSeparator);

        boolean isUsePreferences();

        void setUsePreferences(boolean usePreferences);

        String getPattern();

        void setPattern(String pattern);

        void setTimeZoneIds(List<String> timeZoneIds);

        UserTimeZone.Use getTimeZoneUse();

        void setTimeZoneUse(UserTimeZone.Use use);

        String getTimeZoneId();

        void setTimeZoneId(String timeZoneId);

        Integer getTimeZoneOffsetHours();

        void setTimeZoneOffsetHours(Integer timeZoneOffsetHours);

        Integer getTimeZoneOffsetMinutes();

        void setTimeZoneOffsetMinutes(Integer timeZoneOffsetMinutes);

        boolean isWrap();

        void setWrap(boolean wrap);
    }
}
