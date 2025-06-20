package stroom.query.language.functions;

import stroom.query.api.UserTimeZone;
import stroom.util.shared.NullSafe;

import java.time.ZoneId;
import java.time.ZoneOffset;

public class UserTimeZoneUtil {

    public static ZoneId getZoneId(final UserTimeZone userTimeZone) {
        if (userTimeZone != null) {
            if (UserTimeZone.Use.UTC.equals(userTimeZone.getUse())) {
                return ZoneOffset.UTC;
            } else if (UserTimeZone.Use.LOCAL.equals(userTimeZone.getUse())) {
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
