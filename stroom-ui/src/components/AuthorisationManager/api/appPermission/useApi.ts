import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";

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
  const { stroomBaseServiceUrl } = useConfig();
  const {
    httpGetJson,
    httpPostEmptyResponse,
    httpDeleteEmptyResponse,
  } = useHttpClient();

  return {
    getPermissionsForUser: React.useCallback(
      (userUuid: string): Promise<string[]> =>
        httpGetJson(`${stroomBaseServiceUrl}/appPermissions/v1/${userUuid}`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    getAllPermissionNames: React.useCallback(
      (): Promise<string[]> =>
        httpGetJson(`${stroomBaseServiceUrl}/appPermissions/v1`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    addAppPermission: React.useCallback(
      (userUuid: string, permissionName: string): Promise<void> =>
        httpPostEmptyResponse(
          `${stroomBaseServiceUrl}/appPermissions/v1/${userUuid}/${permissionName}`,
        ),
      [stroomBaseServiceUrl, httpPostEmptyResponse],
    ),
    removeAppPermission: React.useCallback(
      (userUuid: string, permissionName: string): Promise<void> =>
        httpDeleteEmptyResponse(
          `${stroomBaseServiceUrl}/appPermissions/v1/${userUuid}/${permissionName}`,
        ),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
  };
};

export default useApi;
