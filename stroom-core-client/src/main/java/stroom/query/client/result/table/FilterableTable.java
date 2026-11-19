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

package stroom.query.client.result.table;

import stroom.query.api.Column;
import stroom.query.api.ConditionalFormattingRule;

import com.google.gwt.dom.client.Element;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;

public interface FilterableTable {

    List<Column> getColumns();

    Element getFilterButton(Column column);

    ColumnValuesDataSupplier getDataSupplier(Column column,
                                             List<ConditionalFormattingRule> conditionalFormattingRules);

    FilterCellManager getFilterCellManager();

    /// Listens for the table's columns or data changing, until the registration is removed.
    ///
    /// @param listener What to run.
    /// @return The registration, for the listener's owner to own.
    HandlerRegistration addUpdateListener(Runnable listener);
}
