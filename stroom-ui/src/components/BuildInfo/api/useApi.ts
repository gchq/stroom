import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import { BuildInfo } from "./types";

interface UseApi {
  getBuildInfo: () => Promise<BuildInfo>;
}

const useApi = (): UseApi => {
  const { httpGetJson } = useHttpClient();
  const { stroomBaseServiceUrl } = useConfig();
  return {
    getBuildInfo: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/build-info/v1`),
      [httpGetJson, stroomBaseServiceUrl],
    ),
  };
};

export default useApi;
