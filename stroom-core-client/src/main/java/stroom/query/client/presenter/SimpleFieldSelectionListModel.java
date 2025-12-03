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

package stroom.query.client.presenter;

import stroom.item.client.AbstractSelectionListModel;
import stroom.query.api.datasource.QueryField;
import stroom.task.client.TaskMonitorFactory;

import java.util.function.Consumer;

public class SimpleFieldSelectionListModel
        extends AbstractSelectionListModel<QueryField, FieldInfoSelectionItem>
        implements FieldSelectionListModel {

    @Override
    public void findFieldByName(final String fieldName, final Consumer<QueryField> consumer) {
        if (fieldName != null) {
            items.stream()
                    .filter(fieldInfo -> fieldInfo.getLabel().equals(fieldName))
                    .findFirst()
                    .ifPresent(item -> consumer.accept(item.getField()));
        } else {
            items.stream()
                    .findFirst()
                    .ifPresent(item -> consumer.accept(item.getField()));
        }
    }

    @Override
    public FieldInfoSelectionItem wrap(final QueryField item) {
        return new FieldInfoSelectionItem(item);
    }

    @Override
    public QueryField unwrap(final FieldInfoSelectionItem selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getField();
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {

    }
}
