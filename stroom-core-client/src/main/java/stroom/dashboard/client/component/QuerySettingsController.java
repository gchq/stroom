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


import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A Query pane's settings: Basic and Selection Query, whose fields are the query's data source.
public class QuerySettingsController extends ComponentSettingsDialogController {

    /// Made by GIN, with its content and its tabs.
    @Inject
    public QuerySettingsController(final EventBus eventBus,
                                   final ComponentSettingsDialog view,
                                   final BasicQuerySettingsController basicSettings,
                                   final SelectionHandlersController selectionQuery) {
        super(eventBus, view);
        own(basicSettings);
        own(selectionQuery);

        selectionQuery.setDataSourceRefConsumer(consumer ->
                consumer.accept(basicSettings.getDataSource()));

        addTab("Basic", basicSettings);
        addTab("Selection Query", selectionQuery);
    }
}
