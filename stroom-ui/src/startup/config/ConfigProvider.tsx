import * as React from "react";

import ConfigContext from "./ConfigContext";
import useGlobalConfigResource from "./useGlobalConfigResource";
import CustomLoader from "../../components/CustomLoader";

const ConfigProvider: React.FunctionComponent = ({ children }) => {
  const { config } = useGlobalConfigResource();

  if (!config) {
    return (
      <CustomLoader title="Stroom" message="Loading Config. Please wait..." />
    );
  }

  return (
    <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>
  );
};

export default ConfigProvider;
