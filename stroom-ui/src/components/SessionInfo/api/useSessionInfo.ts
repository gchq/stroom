// Service layer to define state to store and invoke rest http functions to populate state

import * as React from "react";
import useApi from "./useApi";
import { SessionInfo } from "./types";

const useSessionInfo = (): SessionInfo => {
  // Get the API object the provides the function that returns the promise
  const { getSessionInfo } = useApi();

  // Declare the React state object to hold the response from the REST API
  const [sessionInfo, setSessionInfo] = React.useState<SessionInfo>({
    userName: "TBD",
    nodeName: "TBD",
    buildInfo: {
      upDate: "TBD",
      buildDate: "TBD",
      buildVersion: "TBD",
    },
  });

  // Use an effect to set the build info state when the component is mounted
  React.useEffect(() => {
    getSessionInfo().then(setSessionInfo);
  }, [setSessionInfo, getSessionInfo]);

  return sessionInfo;
};

export default useSessionInfo;
