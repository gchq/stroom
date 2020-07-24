import { useCallback } from "react";
import { useHttpClient2 } from "lib/useHttpClient";
import useUrlFactory from "lib/useUrlFactory";

export interface UserAndPermissions {
  userId: string;
  permission: string[];
}

export interface User {
  id?: number;
  version?: number;
  createTimeMs?: number;
  createUser?: string;
  updateTimeMs?: number;
  updateUser?: string;
  name?: string;
  uuid?: string;
  group: boolean;
  enabled: boolean;
}

interface AppPermissionResource {
  getUserAndPermissions: () => Promise<UserAndPermissions>;
}

export const useAppPermissionResource = (): AppPermissionResource => {
  const { httpGet } = useHttpClient2();
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/permission/app/v1");

  const getUserAndPermissions = useCallback(() => httpGet(`${resource}`), [
    resource,
    httpGet,
  ]);

  return {
    getUserAndPermissions,
  };
};
