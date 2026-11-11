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

package stroom.query.client.component;

import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QueryOptionSet;
import stroom.query.api.datasource.QueryOptionUtil;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// A dialog for a term's field-level query options ([QueryOptions]), made for one use. The
/// controls are made from the definitions of the field's [QueryOptionSet], which the field's data
/// source gives.
public class QueryOptionsController extends Controller {

    private final QueryOptions view;
    private QueryOptionSet queryOptionSet;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus The application's event bus, which the dialog is shown on.
    /// @param view     The dialog's content.
    @Inject
    public QueryOptionsController(final EventBus eventBus, final QueryOptions view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnChange(this::updateDisabledOptions);
    }

    /// Shows the dialog for a term's field, owned by `opener` until it is closed.
    ///
    /// @param opener  What shows it.
    /// @param field   The term's field, which must have a [QueryOptionSet].
    /// @param options The options the term holds, or null for none.
    /// @param onOk    Given the options for the term to hold when OK is pressed: only those that
    ///                differ from their defaults, or null if none do.
    public void edit(final HasControllerNode opener,
                     final QueryField field,
                     final Map<String, String> options,
                     final Consumer<Map<String, String>> onOk) {
        Objects.requireNonNull(onOk, "onOk");
        queryOptionSet = Objects.requireNonNull(field.getQueryOptionSet(), "queryOptionSet");
        view.setDefinitions(queryOptionSet.getDefinitions());
        view.setValues(QueryOptionUtil.resolve(queryOptionSet, options));
        updateDisabledOptions();

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Query Options - " + field.getFldName())
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    e.hide();
                    if (e.isOk()) {
                        onOk.accept(QueryOptionUtil.normalise(queryOptionSet, view.getValues()));
                    }
                })
                .fire();
    }

    // Some options apply only given others' values, e.g. a fuzzy match's edit distance
    private void updateDisabledOptions() {
        view.setDisabledOptions(QueryOptionUtil.getDisabledOptionIds(queryOptionSet, view.getValues()));
    }
}
