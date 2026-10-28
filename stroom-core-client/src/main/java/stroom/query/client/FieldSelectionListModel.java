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

package stroom.query.client;

import stroom.item.client.SelectionListModel;
import stroom.query.api.datasource.QueryField;
import stroom.task.client.TaskMonitorFactory;

import java.util.function.Consumer;

public interface FieldSelectionListModel extends
        SelectionListModel<QueryField, FieldInfoSelectionItem> {
    void findFieldByName(String fieldName, Consumer<QueryField> consumer);

    /// Says what shows the progress of the requests it makes, e.g. the presenter that uses it.
    ///
    /// @param taskMonitorFactory What shows their progress.
    void setTaskMonitorFactory(TaskMonitorFactory taskMonitorFactory);
}
