import * as React from "react";

import { UiConfig } from "api/stroom";

export const defaultValue: UiConfig = {
  theme: {
    backgroundColor: "red",
  },
  uiPreferences: {
    dateFormat: "YYYY-MM-DDTHH:mm:ss.SSS",
  },
};

export default React.createContext<UiConfig>(defaultValue);
