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

package stroom.dashboard.server.format;

import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Format.Type;

public class FormatterFactory {
    private final String dateTimeLocale;

    public FormatterFactory(final String dateTimeLocale) {
        this.dateTimeLocale = dateTimeLocale;
    }

    public Formatter create(final Field field) {
        if (field == null) {
            return Unformatted.create();
        }

        Type type = Type.GENERAL;
        if (field.getFormat() != null) {
            type = field.getFormat().getType();
        }

        switch (type) {
        case TEXT:
            return StringFormatter.create();
        case NUMBER:
            return NumberFormatter.create(field.getFormat().getSettings());
        case DATE_TIME:
            return DateFormatter.create(field.getFormat().getSettings(), dateTimeLocale);
        default:
            return Unformatted.create();
        }
    }
}
