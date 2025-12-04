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

package stroom.query.common.v2.format;

import stroom.query.api.Column;
import stroom.query.language.functions.Val;

import java.util.HashMap;
import java.util.Map;

public class ColumnFormatter {

    private final FormatterFactory formatterFactory;
    private final Map<Column, Formatter> formatterCache = new HashMap<>();

    public ColumnFormatter(final FormatterFactory formatterFactory) {
        this.formatterFactory = formatterFactory;
    }

    public String format(final Column column, final Val value) {
        return formatterCache.computeIfAbsent(column, k ->
                        formatterFactory.create(column))
                .format(value);
    }
}
