import * as React from "react";

import { UserPreferences } from "api/stroom";

export const defaultValue: UserPreferences = {
  theme: "Dark",
  font: "Roboto",
  fontSize: "Medium",
  dateTimePattern: "YYYY-MM-DDTHH:mm:ss.SSS",
  timeZone: {
    use: "UTC",
  },
};

export default React.createContext<UserPreferences>(defaultValue);
