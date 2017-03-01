/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.client.table;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.shared.DateTimeFormatSettings;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Format;
import stroom.dashboard.shared.Format.Type;
import stroom.dashboard.shared.FormatSettings;
import stroom.dashboard.shared.NumberFormatSettings;
import stroom.dashboard.shared.TimeZone;
import stroom.util.shared.EqualsUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;

public class FormatPresenter extends MyPresenterWidget<FormatPresenter.FormatView>implements FormatUihandlers {
    private final TimeZones timeZones;
    private Type type;
    private TablePresenter tablePresenter;
    private Field field;
    @Inject
    public FormatPresenter(final EventBus eventBus, final FormatView view, final TimeZones timeZones) {
        super(eventBus, view);
        this.timeZones = timeZones;

        view.setUiHandlers(this);
        view.setTypes(Format.TYPES);
        getView().setTimeZoneIds(timeZones.getIds());
    }

    public void show(final TablePresenter tablePresenter, final Field field) {
        this.tablePresenter = tablePresenter;
        this.field = field;

        final Format format = field.getFormat();
        if (format == null || format.getType() == null) {
            setType(Type.GENERAL);
        } else {
            setNumberSettings(format.getSettings());
            setDateTimeSettings(format.getSettings());
            setType(format.getType());
        }

        if (format != null && format.getWrap() != null && format.getWrap()) {
            getView().setWrap(true);
        } else {
            getView().setWrap(false);
        }

        final PopupSize popupSize = new PopupSize(390, 217, 390, 217, true);
        ShowPopupEvent.fire(tablePresenter, this, PopupType.OK_CANCEL_DIALOG, popupSize,
                "Format '" + field.getName() + "'", this);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final Format format = getFormat();
            if (!EqualsUtil.isEquals(format, field.getFormat())) {
                field.setFormat(format);
                tablePresenter.setDirty(true);
                tablePresenter.clearAndRefresh();
            }
        }

        HidePopupEvent.fire(tablePresenter, this);
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
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
        final Format format = new Format(type);
        if (Type.NUMBER.equals(type)) {
            format.setSettings(getNumberSettings());
        } else if (Type.DATE_TIME.equals(type)) {
            format.setSettings(getDateTimeSettings());
        }

        if (getView().isWrap()) {
            format.setWrap(true);
        } else {
            format.setWrap(null);
        }

        return format;
    }

    private FormatSettings getNumberSettings() {
        final NumberFormatSettings numberFormatSettings = new NumberFormatSettings();
        numberFormatSettings.setDecimalPlaces(getView().getDecimalPlaces());
        numberFormatSettings.setUseSeparator(getView().isUseSeparator());
        return numberFormatSettings;
    }

    private void setNumberSettings(final FormatSettings settings) {
        if (settings == null || !(settings instanceof NumberFormatSettings)) {
            getView().setDecimalPlaces(0);
            getView().setUseSeparator(false);
        } else {
            final NumberFormatSettings numberFormatSettings = (NumberFormatSettings) settings;
            getView().setDecimalPlaces(numberFormatSettings.getDecimalPlaces());
            getView().setUseSeparator(numberFormatSettings.getUseSeparator());
        }
    }

    private FormatSettings getDateTimeSettings() {
        final DateTimeFormatSettings dateTimeFormatSettings = new DateTimeFormatSettings();
        dateTimeFormatSettings.setPattern(getView().getPattern());
        dateTimeFormatSettings.setTimeZone(getTimeZone());

        return dateTimeFormatSettings;
    }

    private void setDateTimeSettings(final FormatSettings settings) {
        TimeZone timeZone = TimeZone.local();

        if (settings == null || !(settings instanceof DateTimeFormatSettings)) {
            getView().setPattern(null);
        } else {
            final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) settings;
            getView().setPattern(dateTimeFormatSettings.getPattern());

            if (dateTimeFormatSettings.getTimeZone() != null) {
                timeZone = dateTimeFormatSettings.getTimeZone();
            }
        }

        setTimeZone(timeZone);
    }

    private TimeZone getTimeZone() {
        return new TimeZone(getView().getTimeZoneUse(), getView().getTimeZoneId(), getView().getTimeZoneOffsetHours(),
                getView().getTimeZoneOffsetMinutes());
    }

    private void setTimeZone(final TimeZone timeZone) {
        getView().setTimeZoneUse(timeZone.getUse());

        if (timeZone.getId() == null) {
            getView().setTimeZoneId(timeZones.getTimeZone());
        } else {
            getView().setTimeZoneId(timeZone.getId());
        }

        getView().setTimeZoneOffsetHours(timeZone.getOffsetHours());
        getView().setTimeZoneOffsetMinutes(timeZone.getOffsetMinutes());
    }

    public interface FormatView extends View, HasUiHandlers<FormatUihandlers> {
        void setTypes(List<Type> types);

        void setType(Type type);

        int getDecimalPlaces();

        void setDecimalPlaces(int decimalPlaces);

        boolean isUseSeparator();

        void setUseSeparator(boolean useSeparator);

        String getPattern();

        void setPattern(String pattern);

        void setTimeZoneIds(List<String> timeZoneIds);

        TimeZone.Use getTimeZoneUse();

        void setTimeZoneUse(TimeZone.Use use);

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
