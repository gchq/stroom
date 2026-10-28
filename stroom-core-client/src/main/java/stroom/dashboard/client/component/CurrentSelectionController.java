/*
 * Copyright 2025 Crown Copyright
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

import stroom.dashboard.client.main.DashboardContext;
import stroom.data.client.CopyTextUtil;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// A dashboard's current selection ([CurrentSelection]): a press on a value copies it, or inserts
/// it where its owner says.
public class CurrentSelectionController extends Controller {

    private final CurrentSelection view;
    private Consumer<String> onInsert;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public CurrentSelectionController(final EventBus eventBus, final CurrentSelection view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnMouseDown(event -> CopyTextUtil.onClick(event, this, onInsert));
    }

    /// Shows a dashboard's current selection.
    ///
    /// @param dashboardContext The dashboard's context.
    /// @param showInsert       Whether each value can be inserted as well as copied.
    public void refresh(final DashboardContext dashboardContext, final boolean showInsert) {
        view.setHtml(dashboardContext.toSafeHtml(showInsert));
    }

    /// @param onInsert Given a value the user inserts.
    public void setOnInsert(final Consumer<String> onInsert) {
        this.onInsert = onInsert;
    }
}
