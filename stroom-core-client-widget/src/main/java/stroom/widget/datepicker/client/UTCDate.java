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

/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package stroom.widget.datepicker.client;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * A simple wrapper around a native JS Date object.
 */
public class UTCDate extends JavaScriptObject {

    /**
     * Non directly instantiable, use one of the {@link #create()} methods.
     */
    protected UTCDate() {
    }

    /**
     * Creates a new date with the current time.
     */
    public static native UTCDate create() /*-{
        return new Date();
    }-*/;

    /**
     * Creates a new date with the specified internal representation, which is the
     * number of milliseconds since midnight on January 1st, 1970. This is the
     * same representation returned by {@link #getTime()}.
     */
    public static native UTCDate create(double milliseconds) /*-{
        return new Date(milliseconds);
    }-*/;

    /**
     * Creates a new date using the specified UTC values.
     */
    public static native UTCDate create(int year,
                                        int month) /*-{
        return new Date(Date.UTC(year, month, 1, 0, 0, 0, 0));
    }-*/;

    /**
     * Creates a new date using the specified UTC values.
     */
    public static native UTCDate create(int year,
                                        int month,
                                        int dayOfMonth) /*-{
        return new Date(Date.UTC(year, month, dayOfMonth, 0, 0, 0, 0));
    }-*/;

    /**
     * Creates a new date using the specified UTC values.
     */
    public static native UTCDate create(int year,
                                        int month,
                                        int dayOfMonth,
                                        int hours) /*-{
        return new Date(Date.UTC(year, month, dayOfMonth, hours, 0, 0, 0));
    }-*/;

    /**
     * Creates a new date using the specified UTC values.
     */
    public static native UTCDate create(int year,
                                        int month,
                                        int dayOfMonth,
                                        int hours,
                                        int minutes) /*-{
       return new Date(Date.UTC(year, month, dayOfMonth, hours, minutes, 0, 0));
    }-*/;

    /**
     * Creates a new date using the specified UTC values.
     */
    public static native UTCDate create(int year,
                                        int month,
                                        int dayOfMonth,
                                        int hours,
                                        int minutes,
                                        int seconds) /*-{
        return new Date(Date.UTC(year, month, dayOfMonth, hours, minutes, seconds, 0));
    }-*/;

    /**
     * Creates a new date using the specified UTC values.
     */
    public static native UTCDate create(int year,
                                        int month,
                                        int dayOfMonth,
                                        int hours,
                                        int minutes,
                                        int seconds,
                                        int millis) /*-{
        return new Date(Date.UTC(year, month, dayOfMonth, hours, minutes, seconds, millis));
    }-*/;

    /**
     * Creates a new date from a string to be parsed.
     */
    public static native UTCDate create(String dateString) /*-{
        var ms = Date.parse(dateString);
        if (isNaN(ms)) {
            return null;
        }
        return new Date(ms);
    }-*/;

    /**
     * Creates a new date from a string to be parsed.
     */
    public static native Double parse(String dateString) /*-{
        var ms = Date.parse(dateString);
        if (isNaN(ms)) {
            return null;
        }
        return ms;
    }-*/;

    /**
     * Returns the internal millisecond representation of the date, the number of
     * milliseconds since midnight on January 1st, 1970.
     */
    public final native double getTime() /*-{
        return this.getTime();
    }-*/;

    /**
     * Returns the day of the month, in UTC.
     */
    public final native int getDate() /*-{
        return this.getUTCDate();
    }-*/;

    /**
     * Returns the day of the week, from <code>0</code> (Sunday) to <code>6</code>
     * Saturday, in UTC.
     */
    public final native int getDay() /*-{
        return this.getUTCDay();
    }-*/;

    /**
     * Returns the four-digit year, in UTC.
     */
    public final native int getFullYear() /*-{
        return this.getUTCFullYear();
    }-*/;

    /**
     * Returns the hour, between <code>0</code> (midnight) and <code>23</code>, in
     * UTC.
     */
    public final native int getHours() /*-{
        return this.getUTCHours();
    }-*/;

    /**
     * Returns the minutes, between <code>0</code> and <code>59</code>, in UTC.
     */
    public final native int getMinutes() /*-{
        return this.getUTCMinutes();
    }-*/;

    /**
     * Returns the month, from <code>0</code> (January) to <code>11</code>
     * December, in UTC.
     */
    public final native int getMonth() /*-{
        return this.getUTCMonth();
    }-*/;

    /**
     * Returns the seconds, between <code>0</code> and <code>59</code>, in UTC.
     */
    public final native int getSeconds() /*-{
        return this.getUTCSeconds();
    }-*/;

    /**
     * Returns the milliseconds, between <code>0</code> and <code>999</code>, in
     * UTC.
     */
    public final native int getMilliseconds() /*-{
        return this.getUTCMilliseconds();
    }-*/;

    /**
     * Sets the internal date representation. Returns the
     * <code>milliseconds</code> argument.
     */
    public final native double setTime(double milliseconds) /*-{
        this.setTime(milliseconds);
        return this.getTime();
    }-*/;

    /**
     * Sets the day of the month, in UTC. Returns the millisecond representation
     * of the adjusted date.
     */
    public final native double setDate(int dayOfMonth) /*-{
        this.setUTCDate(dayOfMonth);
        return this.getTime();
    }-*/;

    /**
     * Sets the year, in UTC. Returns the millisecond representation of the
     * adjusted date.
     */
    public final native double setFullYear(int year) /*-{
        this.setUTCFullYear(year);
        return this.getTime();
    }-*/;

    /**
     * Sets the year and month, in UTC. Returns the millisecond representation of
     * the adjusted date.
     */
    public final native double setFullYear(int year,
                                           int month) /*-{
        this.setUTCFullYear(year, month);
        return this.getTime();
    }-*/;

    /**
     * Sets the year, month, and day, in UTC. Returns the millisecond
     * representation of the adjusted date.
     */
    public final native double setFullYear(int year,
                                           int month,
                                           int day) /*-{
        this.setUTCFullYear(year, month, day);
        return this.getTime();
    }-*/;

    /**
     * Sets the hour, in UTC. Returns the millisecond representation of the
     * adjusted date.
     */
    public final native double setHours(int hours) /*-{
        this.setUTCHours(hours);
        return this.getTime();
    }-*/;

    /**
     * Sets the hour and minutes, in UTC. Returns the millisecond representation
     * of the adjusted date.
     */
    public final native double setHours(int hours,
                                        int minutes) /*-{
        this.setUTCHours(hours, minutes);
        return this.getTime();
    }-*/;

    /**
     * Sets the hour, minutes, and seconds, in UTC. Returns the millisecond
     * representation of the adjusted date.
     */
    public final native double setHours(int hours,
                                        int minutes,
                                        int seconds) /*-{
        this.setUTCHours(hours, minutes, seconds);
        return this.getTime();
    }-*/;

    /**
     * Sets the hour, minutes, seconds, and milliseconds, in UTC. Returns the
     * millisecond representation of the adjusted date.
     */
    public final native double setHours(int hours,
                                        int minutes,
                                        int seconds,
                                        int milliseconds) /*-{
        this.setUTCHours(hours, minutes, seconds, milliseconds);
        return this.getTime();
    }-*/;

    /**
     * Sets the minutes, in UTC. Returns the millisecond representation of the
     * adjusted date.
     */
    public final native double setMinutes(int minutes) /*-{
        this.setUTCMinutes(minutes);
        return this.getTime();
    }-*/;

    /**
     * Sets the minutes and seconds, in UTC. Returns the millisecond
     * representation of the adjusted date.
     */
    public final native double setMinutes(int minutes,
                                          int seconds) /*-{
        this.setUTCMinutes(minutes, seconds);
        return this.getTime();
    }-*/;

    /**
     * Sets the minutes, seconds, and milliseconds, in UTC. Returns the
     * millisecond representation of the adjusted date.
     */
    public final native double setMinutes(int minutes,
                                          int seconds,
                                          int milliseconds) /*-{
        this.setUTCMinutes(minutes, seconds, milliseconds);
        return this.getTime();
    }-*/;

    /**
     * Sets the month, in UTC. Returns the millisecond representation of the
     * adjusted date.
     */
    public final native double setMonth(int month) /*-{
        this.setUTCMonth(month);
        return this.getTime();
    }-*/;

    /**
     * Sets the month and day, in UTC. Returns the millisecond representation of
     * the adjusted date.
     */
    public final native double setMonth(int month,
                                        int dayOfMonth) /*-{
        this.setUTCMonth(month, dayOfMonth);
        return this.getTime();
    }-*/;

    /**
     * Sets the seconds, in UTC. Returns the millisecond representation of the
     * adjusted date.
     */
    public final native double setSeconds(int seconds) /*-{
        this.setUTCSeconds(seconds);
        return this.getTime();
    }-*/;

    /**
     * Sets the seconds and milliseconds, in UTC. Returns the millisecond
     * representation of the adjusted date.
     */
    public final native double setSeconds(int seconds,
                                          int milliseconds) /*-{
        this.setUTCSeconds(seconds, milliseconds);
        return this.getTime();
    }-*/;

    /**
     * Sets the milliseconds, in UTC. Returns the millisecond
     * representation of the adjusted date.
     */
    public final native double setMilliseconds(int milliseconds) /*-{
        this.setUTCMilliseconds(milliseconds);
        return this.getTime();
    }-*/;

    /**
     * The toISOString() method of Date instances returns a string representing this date in the date time string
     * format, a simplified format based on ISO 8601, which is always 24 or 27 characters long
     * (YYYY-MM-DDTHH:mm:ss.sssZ or ±YYYYYY-MM-DDTHH:mm:ss.sssZ, respectively).
     * The timezone is always UTC, as denoted by the suffix Z.
     */
    public final native String toISOString() /*-{
        return this.toISOString();
    }-*/;

    /**
     * Returns a date and time string in UTC.
     */
    public final native String toUTCString() /*-{
        return this.toUTCString();
    }-*/;
}
