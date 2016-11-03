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

package stroom.widget.customdatebox.client;

import java.util.Date;

import com.google.gwt.i18n.client.DateTimeFormat;

public final class ClientDateUtil {
    private ClientDateUtil() {
        // Utility class.
    }

    public static String createDateTimeString(final Long ms) {
        if (ms == null) {
            return null;
        }

        final DateTimeFormat format = new CustomDateTimeFormat();
        return format.format(new Date(ms));
    }

    @SuppressWarnings("deprecation")
    public static Date getInitialDate() {
        final Date now = new Date();
        final Date initial = new Date(now.getYear(), now.getMonth(), now.getDate());
        return initial;
    }
}
