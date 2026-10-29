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

import com.google.gwt.core.client.GWT;

public final class ClientDateUtil {

    private ClientDateUtil() {
        // Utility class.
    }

    public static String toDateString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return MomentJs.nativeToDateString(ms.doubleValue());
    }

    public static String toISOString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return MomentJs.nativeToISOString(ms.doubleValue());
    }

    /**
     * Parses the supplied ISO string into local time
     */
    public static Long fromISOString(final String string) {
        Long millis = null;
        if (string != null && string.trim().length() > 0) {
            try {
                final double res = MomentJs.nativeFromISOString(string.trim());
                if (res > 0) {
                    millis = (long) res;
                }
            } catch (final RuntimeException e) {
                GWT.log(e.getMessage());
            }
        }

        return millis;
    }
}
