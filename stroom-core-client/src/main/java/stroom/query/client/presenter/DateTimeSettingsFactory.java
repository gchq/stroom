package stroom.query.client.presenter;

import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.v2.DateTimeSettings;
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
        final UserPreferences userPreferences = userPreferencesManager.getCurrentPreferences();
        return DateTimeSettings
                .builder()
                .dateTimePattern(userPreferences.getDateTimePattern())
                .timeZone(userPreferences.getTimeZone())
                .localZoneId(timeZones.getLocalTimeZoneId())
                .build();
    }
}
