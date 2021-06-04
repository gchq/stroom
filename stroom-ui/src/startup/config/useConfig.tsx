import * as React from "react";

import ConfigContext from "./ConfigContext";
import { UiConfig } from "../../api/stroom";

const useConfig = (): UiConfig => React.useContext(ConfigContext);

export default useConfig;
