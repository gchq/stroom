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

package stroom.widget.customdatebox.client;

import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.TimeZone;
import com.google.gwt.i18n.client.impl.cldr.DateTimeFormatInfoImpl_en_GB;

import java.util.Date;

public class CustomDateTimeFormat extends DateTimeFormat {

    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final String INNER_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z' z";
    private static final String OFFSET = " +0000";
    private final DateTimeFormat inner = DateTimeFormat.getFormat(INNER_PATTERN);
    private final TimeZone timeZone = TimeZone.createTimeZone(0);

    public CustomDateTimeFormat() {
        super(PATTERN, new DateTimeFormatInfoImpl_en_GB());
    }

    @Override
    public Date parse(final String text) throws IllegalArgumentException {
        return inner.parse(text + OFFSET);
    }

    @Override
    public Date parseStrict(final String text) throws IllegalArgumentException {
        return inner.parseStrict(text + OFFSET);
    }

    @Override
    public int parseStrict(final String text, final int start, final Date date) {
        return inner.parseStrict(text + OFFSET, start, date);
    }

    @Override
    public String format(final Date date) {
        return super.format(date, timeZone);
    }
}
