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

package stroom.widget.datepicker.client;

import java.util.Objects;

public class TimeRecord {

    private final int hour;
    private final int minute;
    private final int second;
    private final int millisecond;

    public TimeRecord(final int hour,
                      final int minute,
                      final int second,
                      final int millisecond) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.millisecond = millisecond;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    public int getMillisecond() {
        return millisecond;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimeRecord that = (TimeRecord) o;
        return hour == that.hour && minute == that.minute && second == that.second && millisecond == that.millisecond;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hour, minute, second, millisecond);
    }

    @Override
    public String toString() {
        return "TimeRecord{" +
                "hour=" + hour +
                ", minute=" + minute +
                ", second=" + second +
                ", millisecond=" + millisecond +
                '}';
    }
}
