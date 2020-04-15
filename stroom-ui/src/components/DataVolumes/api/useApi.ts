import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import FsVolume from "../types/FsVolume";

interface Api {
  update: (volume: FsVolume) => Promise<FsVolume>;
  getVolumes: () => Promise<FsVolume[]>;
  getVolumeById: (id: string) => Promise<FsVolume>;
  deleteVolume: (id: string) => Promise<void>;
  createVolume: () => Promise<FsVolume>;
}

const PATH = "datavolumes/v1";
export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();
  const {
    httpGetJson,
    httpDeleteEmptyResponse,
    httpPostJsonResponse,
    httpPutJsonResponse,
  } = useHttpClient();

  return {
    createVolume: React.useCallback(
      () =>
        httpPostJsonResponse(`${stroomBaseServiceUrl}/${PATH}`, {
          body: JSON.stringify({ todo: "TODO" }),
        }),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),
    deleteVolume: React.useCallback(
      (id: string) =>
        httpDeleteEmptyResponse(`${stroomBaseServiceUrl}/${PATH}/${id}`),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
    getVolumeById: React.useCallback(
      (id: string) => httpGetJson(`${stroomBaseServiceUrl}/${PATH}/${id}`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    getVolumes: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/${PATH}`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    update: React.useCallback(
      (volume: FsVolume) =>
        httpPutJsonResponse(`${stroomBaseServiceUrl}/${PATH}/${volume.id}`, {
          body: JSON.stringify(volume),
        }),
      [stroomBaseServiceUrl, httpPutJsonResponse],
    ),
  };
};

export default useApi;
