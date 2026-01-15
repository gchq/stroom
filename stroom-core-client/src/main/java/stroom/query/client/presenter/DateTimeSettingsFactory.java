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

package stroom.query.client.presenter;

import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.DateTimeSettings;
import stroom.ui.config.shared.UserPreferences;

import javax.inject.Inject;

public class DateTimeSettingsFactory {

    private final TimeZones timeZones;
    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public DateTimeSettingsFactory(final TimeZones timeZones,
                                   final UserPreferencesManager userPreferencesManager) {
        this.timeZones = timeZones;
        this.userPreferencesManager = userPreferencesManager;
    }

    public DateTimeSettings getDateTimeSettings() {
        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        return DateTimeSettings
                .builder()
                .dateTimePattern(userPreferences.getDateTimePattern())
                .timeZone(userPreferences.getTimeZone())
                .localZoneId(timeZones.getLocalTimeZoneId())
                .build();
    }
}
