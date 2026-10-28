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

package stroom.statistics.impl.sql.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.statistics.impl.sql.client.State.Field;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;

/// The New Field and Edit Field dialog of a statistic store's Fields tab, made for one use: OK
/// checks the name (not empty, not another field's, and matching the configured name pattern) and
/// sets it on the field; otherwise the dialog stays open.
public class StatisticsFieldEditController extends Controller {

    private static final String DEFAULT_NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";

    private final StatisticsFieldEdit view;
    private String fieldNamePattern;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus      The application's event bus.
    /// @param view          The content.
    /// @param uiConfigCache Gives the configured name pattern.
    @Inject
    public StatisticsFieldEditController(final EventBus eventBus,
                                         final StatisticsFieldEdit view,
                                         final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        uiConfigCache.get(guard(result -> {
            if (result != null) {
                final String namePattern = result.getNamePattern();
                fieldNamePattern = namePattern == null || namePattern.isEmpty()
                        ? DEFAULT_NAME_PATTERN_VALUE
                        : namePattern;
            }
        }), taskMonitor());
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK sets the name entered on the field
    /// and runs `onOk`, if the name is valid; otherwise it says why and the dialog stays open.
    ///
    /// @param opener          What shows it.
    /// @param caption         The dialog's caption, e.g. "New Field".
    /// @param field           The field to name.
    /// @param otherFieldNames The names of the other fields, which this one may not take.
    /// @param onOk            What to run once the field is named.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final Field field,
                     final Set<String> otherFieldNames,
                     final Runnable onOk) {
        view.setFieldName(field.getName());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String error = validate(view.getFieldName().trim(), otherFieldNames,
                                fieldNamePattern);
                        if (error == null) {
                            field.setName(view.getFieldName().trim());
                            onOk.run();
                            e.hide();
                        } else {
                            AlertEvent.fireError(this, error, e::reset);
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    /// Checks a field's name.
    ///
    /// @param name             The name, trimmed.
    /// @param otherFieldNames  The names of the other fields.
    /// @param fieldNamePattern The pattern names must match, or null for any.
    /// @return Why the name isn't valid, or null if it is.
    static String validate(final String name, final Set<String> otherFieldNames, final String fieldNamePattern) {
        if (name.isEmpty()) {
            return "A statistic field must have a name";
        }
        if (otherFieldNames.contains(name)) {
            return "Another field with this name already exists";
        }
        if (fieldNamePattern != null && !fieldNamePattern.isEmpty() && !name.matches(fieldNamePattern)) {
            return "Invalid name \"" + name + "\" (valid name pattern: " + fieldNamePattern + ")";
        }
        return null;
    }

}
