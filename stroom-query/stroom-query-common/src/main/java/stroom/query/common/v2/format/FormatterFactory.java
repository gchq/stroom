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
import stroom.query.api.DateTimeFormatSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Format.Type;
import stroom.query.api.NumberFormatSettings;

public class FormatterFactory {

    private final DateTimeSettings dateTimeSettings;

    public FormatterFactory(final DateTimeSettings dateTimeSettings) {
        this.dateTimeSettings = dateTimeSettings;
    }

    public Formatter create(final Column column) {
        if (column == null ||
                column.getFormat() == null ||
                column.getFormat().getType() == null) {
            return Unformatted.create();
        }

        final Type type = column.getFormat().getType();
        return switch (type) {
            case TEXT -> StringFormatter.create();
            case NUMBER -> NumberFormatter.create((NumberFormatSettings) column.getFormat().getSettings());
            case DATE_TIME -> DateTimeFormatter.create((DateTimeFormatSettings) column.getFormat().getSettings(),
                    dateTimeSettings);
            default -> Unformatted.create();
        };
    }
}
