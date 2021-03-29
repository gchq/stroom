import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import FsVolume from "../types/FsVolume";
import useUrlFactory from "lib/useUrlFactory";

interface Api {
  update: (volume: FsVolume) => Promise<FsVolume>;
  getVolumes: () => Promise<FsVolume[]>;
  getVolumeById: (id: string) => Promise<FsVolume>;
  deleteVolume: (id: string) => Promise<void>;
  createVolume: () => Promise<FsVolume>;
}

export const useApi = (): Api => {
  const { apiUrl } = useUrlFactory();
  const {
    httpGetJson,
    httpDeleteEmptyResponse,
    httpPostJsonResponse,
    httpPutJsonResponse,
  } = useHttpClient();

  const resource = apiUrl("datavolumes/v1");

  return {
    createVolume: React.useCallback(
      () =>
        httpPostJsonResponse(resource, {
          body: JSON.stringify({ todo: "TODO" }),
        }),
      [resource, httpPostJsonResponse],
    ),
    deleteVolume: React.useCallback(
      (id: string) => httpDeleteEmptyResponse(`${resource}/${id}`),
      [resource, httpDeleteEmptyResponse],
    ),
    getVolumeById: React.useCallback(
      (id: string) => httpGetJson(`${resource}/${id}`),
      [resource, httpGetJson],
    ),
    getVolumes: React.useCallback(() => httpGetJson(resource), [
      resource,
      httpGetJson,
    ]),
    update: React.useCallback(
      (volume: FsVolume) =>
        httpPutJsonResponse(`${resource}/${volume.id}`, {
          body: JSON.stringify(volume),
        }),
      [resource, httpPutJsonResponse],
    ),
  };
};

export default useApi;
