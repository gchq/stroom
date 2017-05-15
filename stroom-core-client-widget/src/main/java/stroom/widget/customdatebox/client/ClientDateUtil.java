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
        return nativeToDateString(ms.doubleValue());
    }

    public static String toISOString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return nativeToISOString(ms.doubleValue());
    }

    public static Long fromISOString(final String string) {
        Long millis = null;
        if (string != null && string.trim().length() > 0) {
            try {
                double res = nativeFromISOString(string.trim());
                if (res > 0) {
                    millis = (long) res;
                }
            } catch (final Exception e) {
                GWT.log(e.getMessage());
            }
        }

        return millis;
    }


    public static boolean isValid(final String string) {
        if (string == null || string.trim().length() == 0) {
            return false;
        }
        return nativeIsValid(string);
    }

    private static native String nativeToDateString(final double ms)/*-{
       var moment = $wnd.moment(ms);
       return moment.format('YYYY-MM-DD');
    }-*/;

    private static native String nativeToISOString(final double ms)/*-{
       var moment = $wnd.moment(ms);
       return moment.toISOString();
    }-*/;

    private static native double nativeFromISOString(final String date)/*-{
        if ($wnd.moment(date).isValid()) {
           var moment = $wnd.moment(date);
           var date = moment.toDate();
           return date.getTime();
        }
        return -1;
    }-*/;

    private static native boolean nativeIsValid(final String date)/*-{
        return $wnd.moment(date).isValid();
    }-*/;
}
