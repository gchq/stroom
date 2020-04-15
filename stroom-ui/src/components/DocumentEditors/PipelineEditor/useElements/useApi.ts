import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { ElementDefinition, ElementPropertiesByElementIdType } from "./types";
import useConfig from "startup/config/useConfig";

interface Api {
  fetchElements: () => Promise<ElementDefinition[]>;
  fetchElementProperties: () => Promise<ElementPropertiesByElementIdType>;
}

export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();
  const { httpGetJson } = useHttpClient();

  const fetchElements = React.useCallback(
    () =>
      httpGetJson(`${stroomBaseServiceUrl}/elements/v1/elements`, {}, false),
    [stroomBaseServiceUrl, httpGetJson],
  );
  const fetchElementProperties = React.useCallback(
    () =>
      httpGetJson(
        `${stroomBaseServiceUrl}/elements/v1/elementProperties`,
        {},
        false,
      ),
    [stroomBaseServiceUrl, httpGetJson],
  );

  return {
    fetchElementProperties,
    fetchElements,
  };
};

export default useApi;
