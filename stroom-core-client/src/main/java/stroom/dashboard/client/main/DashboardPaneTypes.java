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

package stroom.dashboard.client.main;

import stroom.dashboard.client.component.EmbeddedQueryController;
import stroom.dashboard.client.component.KeyValueInputController;
import stroom.dashboard.client.component.ListInputController;
import stroom.dashboard.client.component.QueryPaneController;
import stroom.dashboard.client.component.TableController;
import stroom.dashboard.client.component.TableFilterInputController;
import stroom.dashboard.client.component.TextInputController;
import stroom.dashboard.client.component.TextPaneController;
import stroom.dashboard.client.component.VisController;

import com.google.inject.Inject;
import com.google.inject.Provider;

/// Registers every type of dashboard pane with the [PaneRegistry], so that a dashboard can create
/// its panes by type. Stroom binds it as an eager singleton, so the types are registered as the
/// app starts.
public class DashboardPaneTypes {

    /// Registers each pane type's controller provider.
    ///
    /// @param paneRegistry           The registry of the dashboard's pane types.
    /// @param queryProvider          Makes the controllers of Query panes.
    /// @param tableProvider          Makes the controllers of Table panes.
    /// @param visProvider            Makes the controllers of Visualisation panes.
    /// @param textProvider           Makes the controllers of Text panes.
    /// @param embeddedQueryProvider  Makes the controllers of Embedded Query panes.
    /// @param keyValueInputProvider  Makes the controllers of Key/Value Input panes.
    /// @param listInputProvider      Makes the controllers of List Input panes.
    /// @param textInputProvider      Makes the controllers of Text Input panes.
    /// @param tableFilterProvider    Makes the controllers of Table Filter panes.
    @Inject
    public DashboardPaneTypes(final PaneRegistry paneRegistry,
                              final Provider<QueryPaneController> queryProvider,
                              final Provider<TableController> tableProvider,
                              final Provider<VisController> visProvider,
                              final Provider<TextPaneController> textProvider,
                              final Provider<EmbeddedQueryController> embeddedQueryProvider,
                              final Provider<KeyValueInputController> keyValueInputProvider,
                              final Provider<ListInputController> listInputProvider,
                              final Provider<TextInputController> textInputProvider,
                              final Provider<TableFilterInputController> tableFilterProvider) {
        paneRegistry.register(QueryPaneController.TYPE, queryProvider);
        paneRegistry.register(TableController.TYPE, tableProvider);
        paneRegistry.register(VisController.TYPE, visProvider);
        paneRegistry.register(TextPaneController.TYPE, textProvider);
        paneRegistry.register(EmbeddedQueryController.TYPE, embeddedQueryProvider);
        paneRegistry.register(KeyValueInputController.TYPE, keyValueInputProvider);
        paneRegistry.register(ListInputController.TYPE, listInputProvider);
        paneRegistry.register(TextInputController.TYPE, textInputProvider);
        paneRegistry.register(TableFilterInputController.TYPE, tableFilterProvider);
    }
}
