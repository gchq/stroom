import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import { SessionInfo } from "./types";

interface UseApi {
  getSessionInfo: () => Promise<SessionInfo>;
}

const useApi = (): UseApi => {
  const { httpGetJson } = useHttpClient();
  const { stroomBaseServiceUrl } = useConfig();
  return {
    getSessionInfo: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/sessionInfo/v1`),
      [httpGetJson, stroomBaseServiceUrl],
    ),
  };
};

export default useApi;
