import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import { IndexVolumeGroup } from "./types";

interface Api {
  update: (indexVolumeGroup: IndexVolumeGroup) => Promise<IndexVolumeGroup>;
  getIndexVolumeGroupNames: () => Promise<string[]>;
  getIndexVolumeGroups: () => Promise<IndexVolumeGroup[]>;
  getIndexVolumeGroup: (name: string) => Promise<IndexVolumeGroup>;
  createIndexVolumeGroup: (name?: string) => Promise<IndexVolumeGroup>;
  deleteIndexVolumeGroup: (id: string) => Promise<void>;
}

export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();
  const {
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
    httpPutJsonResponse,
  } = useHttpClient();

  return {
    getIndexVolumeGroupNames: React.useCallback(
      () =>
        httpGetJson(
          `${stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/names`,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    getIndexVolumeGroups: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/stroom-index/volumeGroup/v1`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    getIndexVolumeGroup: React.useCallback(
      (name: string) =>
        httpGetJson(
          `${stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/${name}`,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    createIndexVolumeGroup: React.useCallback(
      (name?: string) =>
        httpPostJsonResponse(
          `${stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/`,
          { body: JSON.stringify({ name }) },
        ),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),
    deleteIndexVolumeGroup: React.useCallback(
      (id: string) =>
        httpDeleteEmptyResponse(
          `${stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/${id}`,
        ),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
    update: React.useCallback(
      (indexVolumeGroup: IndexVolumeGroup) =>
        httpPutJsonResponse(
          `${stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/`,
          { body: JSON.stringify(indexVolumeGroup) },
        ),
      [stroomBaseServiceUrl, httpPutJsonResponse],
    ),
  };
};

export default useApi;
