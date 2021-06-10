import * as React from "react";

import ConfigContext from "./ConfigContext";
import useConfigResource from "./useConfigResource";
import CustomLoader from "../../components/CustomLoader";

const ConfigProvider: React.FunctionComponent = ({ children }) => {
  const { config } = useConfigResource();

  if (!config) {
    return (
      <CustomLoader title="Stroom" message="Loading Config. Please wait..." />
    );
  } else {
    document.title = config.htmlTitle;
  }

  return (
    <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>
  );
};

export default ConfigProvider;
