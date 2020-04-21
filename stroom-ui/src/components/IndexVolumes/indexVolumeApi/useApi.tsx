import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { IndexVolume, NewIndexVolume, UpdateIndexVolumeDTO } from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface Api {
  update: (indexVolume: UpdateIndexVolumeDTO) => Promise<IndexVolume>;
  getIndexVolumes: () => Promise<IndexVolume[]>;
  getIndexVolumeById: (id: string) => Promise<IndexVolume>;
  deleteIndexVolume: (id: string) => Promise<void>;
  createIndexVolume: (newIndexVolume: NewIndexVolume) => Promise<IndexVolume>;
}

export const useApi = (): Api => {
  const { apiUrl } = useUrlFactory();
  const {
    httpGetJson,
    httpDeleteEmptyResponse,
    httpPostJsonResponse,
    httpPutJsonResponse,
  } = useHttpClient();

  const resource = apiUrl("/stroom-index/volume/v1");

  return {
    createIndexVolume: React.useCallback(
      ({ nodeName, path, indexVolumeGroupName }: NewIndexVolume) =>
        httpPostJsonResponse(resource,
          { body: JSON.stringify({ nodeName, path, indexVolumeGroupName }) },
        ),
      [resource, httpPostJsonResponse],
    ),
    deleteIndexVolume: React.useCallback(
      (id: string) =>
        httpDeleteEmptyResponse(`${resource}/${id}`),
      [resource, httpDeleteEmptyResponse],
    ),
    getIndexVolumeById: React.useCallback(
      (id: string) =>
        httpGetJson(`${resource}/${id}`),
      [resource, httpGetJson],
    ),
    getIndexVolumes: React.useCallback(
      () => httpGetJson(resource),
      [resource, httpGetJson],
    ),
    update: React.useCallback(
      (indexVolume: UpdateIndexVolumeDTO) =>
        httpPutJsonResponse(resource, {
          body: JSON.stringify(indexVolume),
        }),
      [resource, httpPutJsonResponse],
    ),
  };
};

export default useApi;
