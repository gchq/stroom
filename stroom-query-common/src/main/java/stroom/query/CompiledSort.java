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

package stroom.query;

import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;

import java.io.Serializable;

public class CompiledSort implements Serializable {
    private static final long serialVersionUID = 719372020029496497L;

    private int fieldIndex;
    private int order;
    private SortDirection direction;

    public CompiledSort(final int fieldIndex, final Sort sort) {
        this.fieldIndex = fieldIndex;
        this.order = sort.getOrder();
        this.direction = sort.getDirection();
    }

    public int getFieldIndex() {
        return fieldIndex;
    }

    public int getOrder() {
        return order;
    }

    public SortDirection getDirection() {
        return direction;
    }
}
