import * as React from "react";

import ConfigContext from "./ConfigContext";
import useApi from "./useApi";

const ConfigProvider: React.FunctionComponent = ({ children }) => {
  const { config } = useApi();

  if (!config) {
    return <div>Loading. Please wait...</div>;
  }

  return (
    <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>
  );
};

export default ConfigProvider;
