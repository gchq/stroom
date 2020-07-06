import * as React from "react";

import ConfigContext from "./ConfigContext";
import useGlobalConfigResource from "./useGlobalConfigResource";

const ConfigProvider: React.FunctionComponent = ({ children }) => {
  const { config } = useGlobalConfigResource();

  if (!config) {
    return <div>Loading. Please wait...</div>;
  }

  return (
    <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>
  );
};

export default ConfigProvider;
