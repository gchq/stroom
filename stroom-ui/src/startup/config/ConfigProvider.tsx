import * as React from "react";

import ConfigContext from "./ConfigContext";
import { Config } from "./types";

const config: Config = new (class implements Config {
  allowPasswordResets: boolean = (process.env.REACT_APP_ALLOW_PASSWORD_RESETS === "true");
  dateFormat: string = process.env.REACT_APP_DATE_FORMAT;
  defaultApiKeyExpiryInMinutes: string = process.env.REACT_APP_DEFAULT_API_KEY_EXPIRY_IN_MINUTES;
})();

const ConfigProvider: React.FunctionComponent = ({ children }) => {
  return (
    <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>
  );
};

export default ConfigProvider;
