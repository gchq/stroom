import * as React from "react";

import ConfigContext from "./ConfigContext";
import { Config } from "./types";
import Loader from "components/Loader";

const ConfigProvider: React.FunctionComponent = ({ children }) => {
  const [isReady, setIsReady] = React.useState<boolean>(false);
  const [config, setConfig] = React.useState<Config>({});

  React.useEffect(() => {
    // Not using our http client stuff, it depends on things which won't be ready until the config is loaded
    fetch("/config.json")
      .then(r => r.json())
      .then(c => {
        setConfig(c);
        setIsReady(true);
      });
  }, [setIsReady, setConfig]);

  if (!isReady) {
    return <Loader message="Waiting for config" />;
  }

  return (
    <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>
  );
};

export default ConfigProvider;
