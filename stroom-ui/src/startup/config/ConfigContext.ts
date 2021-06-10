import * as React from "react";

import { UiConfig } from "api/stroom";

export const defaultValue: UiConfig = {
  htmlTitle: "Stroom",
  theme: {
    backgroundColour: "red",
  },
};

export default React.createContext<UiConfig>(defaultValue);
