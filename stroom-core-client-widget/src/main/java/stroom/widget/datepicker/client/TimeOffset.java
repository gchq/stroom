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

import stroom.widget.util.client.ClientStringUtil;

import java.util.Objects;

public class TimeOffset {

    private final int hours;
    private final int minutes;

    public TimeOffset(final int hours, final int minutes) {
        this.hours = hours;
        this.minutes = minutes;
    }

    public int getHours() {
        return hours;
    }

    public int getMinutes() {
        return minutes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimeOffset offset = (TimeOffset) o;
        return hours == offset.hours && minutes == offset.minutes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hours, minutes);
    }

    @Override
    public String toString() {
        final String offset = ClientStringUtil.zeroPad(2, hours) + ClientStringUtil.zeroPad(2, minutes);
        if ((hours * 60) + minutes >= 0) {
            return "+" + offset;
        }
        return "-" + offset;
    }
}
