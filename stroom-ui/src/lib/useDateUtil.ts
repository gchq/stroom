import moment from "moment-timezone";
import useUserPreferences from "../startup/config/useUserPreferences";

interface DateUtil {
  toDateString: (value: number) => string;
}

const useDateUtil = (): DateUtil => {
  const {
    dateTimePattern,
    timeZone: { use, id, offsetHours, offsetMinutes },
  } = useUserPreferences();

  const toDateString = (value: number) => {
    let m = moment.utc(value);
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
        m = m.utcOffset(offsetHours + offsetMinutes);
        return m.format(dateTimePattern);
      }
      case "Id": {
        m = m.tz(id);
        return m.format(dateTimePattern);
      }
    }
    return m.format(dateTimePattern);
  };

  return {
    toDateString,
  };
};

export default useDateUtil;
