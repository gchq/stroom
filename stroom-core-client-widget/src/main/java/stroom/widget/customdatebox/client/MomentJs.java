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

public final class MomentJs {

    public static native String nativeToDateString(final double ms)/*-{
       var moment = $wnd.moment(ms);
       return moment.format('YYYY-MM-DD');
    }-*/;

    public static native String nativeToISOString(final double ms)/*-{
       var moment = $wnd.moment(ms);
       return moment.toISOString();
    }-*/;

    /**
     * moment(str) returns the date/time in local time. If the date string has no
     * timezone information it is assumed to be local time.
     * See https://momentjs.com/docs/#/parsing/utc/
     */
    public static native double nativeFromISOString(final String date)/*-{
        if ($wnd.moment(date).isValid()) {
           var moment = $wnd.moment(date);
           var date = moment.toDate();
           return date.getTime();
        }
        return -1;
    }-*/;

    /**
     * moment(date, format) returns the date/time in local time. If the date string has no*
     * timezone information it is assumed to be local time.
     * See https://momentjs.com/docs/#/parsing/utc/
     */
    public static native double nativeFromCustomFormatString(final String date,
                                                             final String format)/*-{
        if ($wnd.moment(date, format).isValid()) {
           var moment = $wnd.moment(date, format);
           var date = moment.toDate();
           return date.getTime();
        }
        return -1;
    }-*/;

    public static native String nativeToDateString(final double ms,
                                                   final String use,
                                                   final String dateTimePattern,
                                                   final String id,
                                                   final Integer offsetMinutes)/*-{
        var m = $wnd.moment.utc(ms);
        switch (use) {
            case "UTC": {
                m = m.utc();
                return m.format(dateTimePattern);
            }
            case "Local": {
                m = m.local();
                return m.format(dateTimePattern);
            }
            case "Offset": {
                m = m.utcOffset(offsetMinutes);
                return m.format(dateTimePattern);
            }
            case "Id": {
                m = m.tz(id);
                return m.format(dateTimePattern);
            }
        }
        return m.format(dateTimePattern);
    }-*/;

    public static native String humanise(final double ms)/*-{
       return $wnd.moment.duration(ms).humanize();
    }-*/;

    public static native String humanise(final double ms, final boolean withSuffix)/*-{
       return $wnd.moment.duration(ms).humanize(withSuffix);
    }-*/;

    public static native String[] getTimeZoneIds()/*-{
        return $wnd.moment.tz.names();
    }-*/;
}
