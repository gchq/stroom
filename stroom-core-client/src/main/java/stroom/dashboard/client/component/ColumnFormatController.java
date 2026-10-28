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

package stroom.dashboard.client.component;

import stroom.query.api.Column;
import stroom.query.api.DateTimeFormatSettings;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.api.FormatSettings;
import stroom.query.api.NumberFormatSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.client.presenter.TimeZones;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.BiConsumer;

/// The Format dialog ([ColumnFormat]) of a table column, made for one use.
public class ColumnFormatController extends Controller {

    // What a column with no date/time settings shows. Matches DateTimeFormatSettings.Builder's own
    // default, so opening a column that has never been formatted shows the value it would save
    private static final boolean DEFAULT_USE_PREFERENCES = true;

    // What a column with no number settings shows, and the fallback when it has them but they are
    // null: NumberFormatSettings' @JsonCreator assigns both fields raw, so a deserialised instance
    // can hold nulls where the view takes primitives. Values match that class's own defaults
    private static final int DEFAULT_DECIMAL_PLACES = 0;
    private static final boolean DEFAULT_USE_SEPARATOR = false;

    private final ColumnFormat view;
    private final TimeZones timeZones;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus  The application's event bus.
    /// @param view      The content.
    /// @param timeZones The named time zones offered.
    @Inject
    public ColumnFormatController(final EventBus eventBus,
                                  final ColumnFormat view,
                                  final TimeZones timeZones) {
        super(eventBus);
        this.view = view;
        this.timeZones = timeZones;
        initWidget(view);
        view.setTypes(Format.TYPES);
        timeZones.getIds(view::setTimeZoneIds);
    }

    /// Shows a column's format in a dialog, owned by `opener` until it is closed. On OK, if the
    /// format was changed, `onChange` is given the column and a copy with the new format.
    ///
    /// @param opener   What shows it.
    /// @param column   The column.
    /// @param onChange Given the column and its copy with the new format.
    public void show(final HasControllerNode opener,
                     final Column column,
                     final BiConsumer<Column, Column> onChange) {
        read(column.getFormat());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(450, 420))
                .caption("Format '" + column.getName() + "'")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final Format newFormat = write();
                        if (!Objects.equals(newFormat, column.getFormat())) {
                            onChange.accept(column, column.copy().format(newFormat).build());
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    /// Fills the dialog with a format, or the defaults for none.
    ///
    /// @param format The format, or null.
    void read(final Format format) {
        final FormatSettings settings = format != null
                ? format.getSettings()
                : null;
        setNumberSettings(settings);
        setDateTimeSettings(settings);
        view.setType(format == null || format.getType() == null
                ? Type.GENERAL
                : format.getType());
        view.setWrap(format != null && format.getWrap() != null && format.getWrap());
    }

    private Format write() {
        final Type type = view.getType();
        FormatSettings settings = null;
        if (Type.NUMBER.equals(type)) {
            settings = new NumberFormatSettings(view.getDecimalPlaces(), view.isUseSeparator());
        } else if (Type.DATE_TIME.equals(type)) {
            settings = new DateTimeFormatSettings(view.isUsePreferences(), view.getPattern(), getTimeZone());
        }
        final Boolean wrap = view.isWrap()
                ? true
                : null;
        return new Format(type, settings, wrap);
    }

    private void setNumberSettings(final FormatSettings settings) {
        if (settings instanceof final NumberFormatSettings numberFormatSettings) {
            view.setDecimalPlaces(Objects.requireNonNullElse(
                    numberFormatSettings.getDecimalPlaces(), DEFAULT_DECIMAL_PLACES));
            view.setUseSeparator(Objects.requireNonNullElse(
                    numberFormatSettings.getUseSeparator(), DEFAULT_USE_SEPARATOR));
        } else {
            view.setDecimalPlaces(DEFAULT_DECIMAL_PLACES);
            view.setUseSeparator(DEFAULT_USE_SEPARATOR);
        }
    }

    private void setDateTimeSettings(final FormatSettings settings) {
        UserTimeZone timeZone = UserTimeZone.utc();
        if (settings instanceof final DateTimeFormatSettings dateTimeFormatSettings) {
            view.setUsePreferences(dateTimeFormatSettings.isUsePreferences());
            view.setPattern(dateTimeFormatSettings.getPattern());
            if (dateTimeFormatSettings.getTimeZone() != null) {
                timeZone = dateTimeFormatSettings.getTimeZone();
            }
        } else {
            view.setUsePreferences(DEFAULT_USE_PREFERENCES);
            view.setPattern(null);
        }
        setTimeZone(timeZone);
    }

    private UserTimeZone getTimeZone() {
        return new UserTimeZone(
                view.getTimeZoneUse(),
                view.getTimeZoneId(),
                view.getTimeZoneOffsetHours(),
                view.getTimeZoneOffsetMinutes());
    }

    private void setTimeZone(final UserTimeZone timeZone) {
        view.setTimeZoneUse(timeZone.getUse());
        view.setTimeZoneId(timeZone.getId() == null
                ? timeZones.getLocalTimeZoneId()
                : timeZone.getId());
        view.setTimeZoneOffsetHours(timeZone.getOffsetHours());
        view.setTimeZoneOffsetMinutes(timeZone.getOffsetMinutes());
    }
}
