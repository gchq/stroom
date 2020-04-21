import * as React from "react";
import useHttpClient from "lib/useHttpClient";
import { WelcomeData } from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface UseApi {
  getWelcomeHtml: () => Promise<WelcomeData>;
}

const useApi = (): UseApi => {
  const { httpGetJson } = useHttpClient();
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/welcome/v1");

  return {
    getWelcomeHtml: React.useCallback(
      () => httpGetJson(resource),
      [resource, httpGetJson],
    ),
  };
};

export default useApi;
