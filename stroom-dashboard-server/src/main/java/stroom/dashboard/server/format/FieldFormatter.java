/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.server.format;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.shared.Field;

import java.util.HashMap;
import java.util.Map;

public class FieldFormatter {
    private final FormatterFactory formatterFactory;
    private final Map<Field, Formatter> formatterCache = new HashMap<>();

    public FieldFormatter(final FormatterFactory formatterFactory) {
        this.formatterFactory = formatterFactory;
    }

    public String format(final Field field, final Val value) {
        Formatter formatter = formatterCache.get(field);
        if (formatter == null) {
            formatter = formatterFactory.create(field);
            formatterCache.put(field, formatter);
        }
        return formatter.format(value);
    }
}
