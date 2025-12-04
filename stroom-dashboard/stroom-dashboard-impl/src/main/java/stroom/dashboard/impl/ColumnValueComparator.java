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

package stroom.dashboard.impl;

import stroom.dashboard.shared.ColumnValue;

import java.util.Comparator;

public class ColumnValueComparator implements Comparator<ColumnValue> {

    private final GenericComparator genericComparator = new GenericComparator();

    @Override
    public int compare(final ColumnValue o1, final ColumnValue o2) {
        return genericComparator.compare(o1.getValue(), o2.getValue());
    }
}
