import * as React from "react";

import ConfigContext from "./ConfigContext";

const useConfig = () => React.useContext(ConfigContext);

export default useConfig;
