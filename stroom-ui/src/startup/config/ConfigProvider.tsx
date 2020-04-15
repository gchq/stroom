import * as React from "react";

import ConfigContext from "./ConfigContext";
import { Config } from "./types";
// import Loader from "components/Loader";

const config: Config = new (class implements Config {
  advertisedUrl: string = process.env.REACT_APP_ADVERTISED_URL;
  allowPasswordResets: boolean = (process.env.REACT_APP_ALLOW_PASSWORD_RESETS === "true");
  clientId: string = process.env.REACT_APP_CLIENT_ID;
  dateFormat: string = process.env.REACT_APP_DATE_FORMAT;
  defaultApiKeyExpiryInMinutes: string = process.env.REACT_APP_DEFAULT_API_KEY_EXPIRY_IN_MINUTES;
  openIdConfigUrl: string = process.env.REACT_APP_OPEN_ID_CONFIG_URL;
  stroomBaseServiceUrl: string = process.env.REACT_APP_API_PATH;
  stroomUiUrl: string = process.env.REACT_APP_STROOM_URL;
})();

const ConfigProvider: React.FunctionComponent = ({ children }) => {
  // const [isReady, setIsReady] = React.useState<boolean>(false);
  // const [config, setConfig] = React.useState<Config>({});
  //
  // React.useEffect(() => {
  //
  //
  //
  //   setConfig(config);
  //   setIsReady(true);
  //
  //   // Not using our http client stuff, it depends on things which won't be ready until the config is loaded
  //   // fetch("config.json")
  //   //   .then(r => r.json())
  //   //   .then(c => {
  //   //     setConfig(c);
  //   //     setIsReady(true);
  //   //   });
  // }, [setIsReady, setConfig]);
  //
  // if (!isReady) {
  //   return <Loader message="Waiting for config" />;
  // }

  return (
    <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>
  );
};

export default ConfigProvider;
