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

package stroom.data.grid.client;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.user.cellview.client.Column;
import stroom.entity.shared.OrderBy;
import stroom.util.shared.HasDisplayValue;

public abstract class OrderByColumn<T, C> extends Column<T, C>implements HasDisplayValue {
    private final OrderBy orderBy;

    public OrderByColumn(final Cell<C> cell, final OrderBy orderBy) {
        super(cell);
        setSortable(true);
        this.orderBy = orderBy;
    }

    public OrderBy getOrderBy() {
        return orderBy;
    }

    @Override
    public String getDisplayValue() {
        return orderBy.getDisplayValue();
    }
}
