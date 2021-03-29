import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { ElementDefinition, ElementPropertiesByElementIdType } from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface Api {
  fetchElements: () => Promise<ElementDefinition[]>;
  fetchElementProperties: () => Promise<ElementPropertiesByElementIdType>;
}

export const useApi = (): Api => {
  const { apiUrl } = useUrlFactory();
  const { httpGetJson } = useHttpClient();
  const resource = apiUrl("/elements/v1");

  const fetchElements = React.useCallback(
    () => httpGetJson(`${resource}/elements`, {}, false),
    [resource, httpGetJson],
  );
  const fetchElementProperties = React.useCallback(
    () => httpGetJson(`${resource}/elementProperties`, {}, false),
    [resource, httpGetJson],
  );

  return {
    fetchElementProperties,
    fetchElements,
  };
};

export default useApi;
