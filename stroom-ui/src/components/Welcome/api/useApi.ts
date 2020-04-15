import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import { WelcomeData } from "./types";

interface UseApi {
  getWelcomeHtml: () => Promise<WelcomeData>;
}

const useApi = (): UseApi => {
  const { httpGetJson } = useHttpClient();
  const { stroomBaseServiceUrl } = useConfig();
  return {
    getWelcomeHtml: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/welcome/v1`),
      [httpGetJson, stroomBaseServiceUrl],
    ),
  };
};

export default useApi;
