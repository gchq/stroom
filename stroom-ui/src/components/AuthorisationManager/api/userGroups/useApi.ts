import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import { StroomUser } from ".";

interface Api {
  fetchUser: (uuid: string) => Promise<StroomUser>;
  findUsers: (
    name?: string,
    isGroup?: boolean,
    uuid?: string,
  ) => Promise<StroomUser[]>;
  findUsersInGroup: (groupUuid: string) => Promise<StroomUser[]>;
  findGroupsForUser: (userUuid: string) => Promise<StroomUser[]>;
  createUser: (name: string, isGroup: boolean) => Promise<StroomUser>;
  deleteUser: (uuid: string) => Promise<void>;
  addUserToGroup: (userUuid: string, groupUuid: string) => Promise<void>;
  removeUserFromGroup: (userUuid: string, groupUuid: string) => Promise<void>;
}

export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();
  const {
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
    httpPutEmptyResponse,
  } = useHttpClient();

  return {
    fetchUser: React.useCallback(
      (userUuid: string): Promise<StroomUser> =>
        httpGetJson(`${stroomBaseServiceUrl}/users/v1/${userUuid}`, {}, false),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    addUserToGroup: React.useCallback(
      (userUuid: string, groupUuid: string) =>
        httpPutEmptyResponse(
          `${stroomBaseServiceUrl}/users/v1/${userUuid}/${groupUuid}`,
        ),
      [stroomBaseServiceUrl, httpPutEmptyResponse],
    ),
    createUser: React.useCallback(
      (name: string, isGroup: boolean) =>
        httpPostJsonResponse(
          `${stroomBaseServiceUrl}/users/v1/create/${name}/${isGroup}`,
        ),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),
    deleteUser: React.useCallback(
      (uuid: string) =>
        httpDeleteEmptyResponse(`${stroomBaseServiceUrl}/users/v1/${uuid}`),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
    findGroupsForUser: React.useCallback(
      (userUuid: string) =>
        httpGetJson(
          `${stroomBaseServiceUrl}/users/v1/groupsForUser/${userUuid}`,
          {},
          false,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    findUsers: React.useCallback(
      (name?: string, isGroup?: boolean, uuid?: string) => {
        var url = new URL(`${stroomBaseServiceUrl}/users/v1`);
        if (name !== undefined && name.length > 0)
          url.searchParams.append("name", name);
        url.searchParams.append("isGroup", (isGroup || false).toString());

        if (uuid !== undefined && uuid.length > 0)
          url.searchParams.append("uuid", uuid);

        return httpGetJson(url.href);
      },
      [stroomBaseServiceUrl, httpGetJson],
    ),
    findUsersInGroup: React.useCallback(
      (groupUuid: string) =>
        httpGetJson(
          `${stroomBaseServiceUrl}/users/v1/usersInGroup/${groupUuid}`,
          {},
          false,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    removeUserFromGroup: React.useCallback(
      (userUuid: string, groupUuid: string) =>
        httpDeleteEmptyResponse(
          `${stroomBaseServiceUrl}/users/v1/${userUuid}/${groupUuid}`,
        ),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
  };
};

export default useApi;
