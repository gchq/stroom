import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import { IndexVolume, NewIndexVolume, UpdateIndexVolumeDTO } from "./types";

interface Api {
  update: (indexVolume: UpdateIndexVolumeDTO) => Promise<IndexVolume>;
  getIndexVolumes: () => Promise<IndexVolume[]>;
  getIndexVolumeById: (id: string) => Promise<IndexVolume>;
  deleteIndexVolume: (id: string) => Promise<void>;
  createIndexVolume: (newIndexVolume: NewIndexVolume) => Promise<IndexVolume>;
}

export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();
  const {
    httpGetJson,
    httpDeleteEmptyResponse,
    httpPostJsonResponse,
    httpPutJsonResponse,
  } = useHttpClient();

  return {
    createIndexVolume: React.useCallback(
      ({ nodeName, path, indexVolumeGroupName }: NewIndexVolume) =>
        httpPostJsonResponse(
          `${stroomBaseServiceUrl}/stroom-index/volume/v1/`,
          { body: JSON.stringify({ nodeName, path, indexVolumeGroupName }) },
        ),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),
    deleteIndexVolume: React.useCallback(
      (id: string) =>
        httpDeleteEmptyResponse(
          `${stroomBaseServiceUrl}/stroom-index/volume/v1/${id}`,
        ),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
    getIndexVolumeById: React.useCallback(
      (id: string) =>
        httpGetJson(`${stroomBaseServiceUrl}/stroom-index/volume/v1/${id}`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    getIndexVolumes: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/stroom-index/volume/v1`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    update: React.useCallback(
      (indexVolume: UpdateIndexVolumeDTO) =>
        httpPutJsonResponse(`${stroomBaseServiceUrl}/stroom-index/volume/v1`, {
          body: JSON.stringify(indexVolume),
        }),
      [stroomBaseServiceUrl, httpPutJsonResponse],
    ),
  };
};

export default useApi;
