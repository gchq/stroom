import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import { SessionInfo } from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface UseApi {
  getSessionInfo: () => Promise<SessionInfo>;
}

const useApi = (): UseApi => {
  const { httpGetJson } = useHttpClient();
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/sessionInfo/v1");

  return {
    getSessionInfo: React.useCallback(() => httpGetJson(resource), [
      resource,
      httpGetJson,
    ]),
  };
};

export default useApi;
