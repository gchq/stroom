import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import useUrlFactory from "lib/useUrlFactory";

interface Api {
  getPermissionsForUser: (userUuid: string) => Promise<string[]>;
  getAllPermissionNames: () => Promise<string[]>;
  addAppPermission: (userUuid: string, permissionName: string) => Promise<void>;
  removeAppPermission: (
    userUuid: string,
    permissionName: string,
  ) => Promise<void>;
}

export const useApi = (): Api => {
  const { apiUrl } = useUrlFactory();
  const {
    httpGetJson,
    httpPostEmptyResponse,
    httpDeleteEmptyResponse,
  } = useHttpClient();

  const resource = apiUrl("/appPermissions/v1");

  return {
    getPermissionsForUser: React.useCallback(
      (userUuid: string): Promise<string[]> =>
        httpGetJson(`${resource}/${userUuid}`),
      [resource, httpGetJson],
    ),
    getAllPermissionNames: React.useCallback(
      (): Promise<string[]> => httpGetJson(resource),
      [resource, httpGetJson],
    ),
    addAppPermission: React.useCallback(
      (userUuid: string, permissionName: string): Promise<void> =>
        httpPostEmptyResponse(`${resource}/${userUuid}/${permissionName}`),
      [resource, httpPostEmptyResponse],
    ),
    removeAppPermission: React.useCallback(
      (userUuid: string, permissionName: string): Promise<void> =>
        httpDeleteEmptyResponse(`${resource}/${userUuid}/${permissionName}`),
      [resource, httpDeleteEmptyResponse],
    ),
  };
};

export default useApi;
