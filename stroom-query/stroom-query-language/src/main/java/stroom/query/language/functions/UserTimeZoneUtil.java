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

package stroom.query.language.functions;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;
import stroom.util.shared.NullSafe;

import java.time.ZoneId;
import java.time.ZoneOffset;

public class UserTimeZoneUtil {

    public static ZoneId getZoneId(final DateTimeSettings dateTimeSettings) {
        if (dateTimeSettings == null) {
            return ZoneOffset.UTC;
        }
        return getZoneId(dateTimeSettings.getTimeZone(), dateTimeSettings.getLocalZoneId());
    }

    public static ZoneId getZoneId(final UserTimeZone userTimeZone,
                                   final String localZoneId) {
        if (userTimeZone != null) {
            if (UserTimeZone.Use.UTC.equals(userTimeZone.getUse())) {
                return ZoneOffset.UTC;
            } else if (UserTimeZone.Use.LOCAL.equals(userTimeZone.getUse())) {
                if (localZoneId != null) {
                    try {
                        return ZoneId.of(localZoneId);
                    } catch (final RuntimeException e) {
                        // Ignore.
                    }
                }
                return ZoneId.systemDefault();

            } else if (UserTimeZone.Use.ID.equals(userTimeZone.getUse())) {
                return ZoneId.of(userTimeZone.getId());
            } else if (UserTimeZone.Use.OFFSET.equals(userTimeZone.getUse())) {
                return ZoneOffset.ofHoursMinutes(
                        NullSafe.getInt(userTimeZone.getOffsetHours()),
                        NullSafe.getInt(userTimeZone.getOffsetMinutes()));
            }
        }
        return ZoneOffset.UTC;
    }
}
