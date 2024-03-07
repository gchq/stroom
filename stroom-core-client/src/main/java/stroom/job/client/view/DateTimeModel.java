package stroom.job.client.view;

import stroom.expression.api.UserTimeZone;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.client.ClientStringUtil;

import com.google.inject.Inject;

public class DateTimeModel {

    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public DateTimeModel(final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;
    }

    public String getTimeZone() {
        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        final UserTimeZone userTimeZone = userPreferences.getTimeZone();
        String timeZone = null;
        switch (userTimeZone.getUse()) {
            case UTC: {
                timeZone = "GMT";
                break;
            }
            case ID: {
                timeZone = userTimeZone.getId();
                break;
            }
            case OFFSET: {
                final String hours = ClientStringUtil.zeroPad(2, userTimeZone.getOffsetHours());
                final String minutes = ClientStringUtil.zeroPad(2, userTimeZone.getOffsetMinutes());
                String offset = hours + minutes;
                if (userTimeZone.getOffsetHours() >= 0 && userTimeZone.getOffsetMinutes() >= 0) {
                    offset = "+" + offset;
                } else {
                    offset = "-" + offset;
                }

                timeZone = "GMT" + offset;
                break;
            }
        }
        return timeZone;
    }
}
