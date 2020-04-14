// Service layer to define state to store and invoke rest http functions to populate state

import * as React from "react";
import useApi from "./useApi";
import { BuildInfo } from "./types";

const useBuildInfo = (): BuildInfo => {
  // Get the API object the provides the function that returns the promise
  const { getBuildInfo } = useApi();

  // Declare the React state object to hold the response from the REST API
  const [buildInfo, setBuildInfo] = React.useState<BuildInfo>({
    userName: "TBD",
    buildVersion: "TBD",
    buildDate: "TBD",
    upDate: "TBD",
    nodeName: "TBD",
  });

  // Use an effect to set the build info state when the component is mounted
  React.useEffect(() => {
    getBuildInfo().then(setBuildInfo);
  }, [setBuildInfo, getBuildInfo]);

  return buildInfo;
};

export default useBuildInfo;
