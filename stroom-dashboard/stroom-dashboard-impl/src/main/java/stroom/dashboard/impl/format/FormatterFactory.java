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

package stroom.dashboard.impl.format;

import stroom.query.api.Column;
import stroom.query.api.Format.Type;

public class FormatterFactory {

    private final String dateTimeLocale;

    public FormatterFactory(final String dateTimeLocale) {
        this.dateTimeLocale = dateTimeLocale;
    }

    public Formatter create(final Column column) {
        if (column == null) {
            return Unformatted.create();
        }

        Type type = Type.GENERAL;
        if (column.getFormat() != null) {
            type = column.getFormat().getType();
        }

        switch (type) {
            case TEXT:
                return StringFormatter.create();
            case NUMBER:
                return NumberFormatter.create(column.getFormat().getSettings());
            case DATE_TIME:
                return DateFormatter.create(column.getFormat().getSettings(), dateTimeLocale);
            default:
                return Unformatted.create();
        }
    }
}
