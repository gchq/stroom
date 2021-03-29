import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { IndexVolumeGroup } from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface Api {
  update: (indexVolumeGroup: IndexVolumeGroup) => Promise<IndexVolumeGroup>;
  getIndexVolumeGroupNames: () => Promise<string[]>;
  getIndexVolumeGroups: () => Promise<IndexVolumeGroup[]>;
  getIndexVolumeGroup: (name: string) => Promise<IndexVolumeGroup>;
  createIndexVolumeGroup: (name?: string) => Promise<IndexVolumeGroup>;
  deleteIndexVolumeGroup: (id: string) => Promise<void>;
}

export const useApi = (): Api => {
  const { apiUrl } = useUrlFactory();
  const {
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
    httpPutJsonResponse,
  } = useHttpClient();

  const resource = apiUrl("/stroom-index/volumeGroup/v1");

  return {
    getIndexVolumeGroupNames: React.useCallback(
      () => httpGetJson(`${resource}/names`),
      [resource, httpGetJson],
    ),
    getIndexVolumeGroups: React.useCallback(() => httpGetJson(resource), [
      resource,
      httpGetJson,
    ]),
    getIndexVolumeGroup: React.useCallback(
      (name: string) => httpGetJson(`${resource}/${name}`),
      [resource, httpGetJson],
    ),
    createIndexVolumeGroup: React.useCallback(
      (name?: string) =>
        httpPostJsonResponse(resource, { body: JSON.stringify({ name }) }),
      [resource, httpPostJsonResponse],
    ),
    deleteIndexVolumeGroup: React.useCallback(
      (id: string) => httpDeleteEmptyResponse(`${resource}/${id}`),
      [resource, httpDeleteEmptyResponse],
    ),
    update: React.useCallback(
      (indexVolumeGroup: IndexVolumeGroup) =>
        httpPutJsonResponse(resource, {
          body: JSON.stringify(indexVolumeGroup),
        }),
      [resource, httpPutJsonResponse],
    ),
  };
};

export default useApi;
