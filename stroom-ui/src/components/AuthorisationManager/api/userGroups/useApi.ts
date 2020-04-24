import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { StroomUser } from ".";
import useUrlFactory from "lib/useUrlFactory";

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
  const { apiUrl } = useUrlFactory();
  const {
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
    httpPutEmptyResponse,
  } = useHttpClient();

  const resource = apiUrl("/users/v1");

  return {
    fetchUser: React.useCallback(
      (userUuid: string): Promise<StroomUser> =>
        httpGetJson(`${resource}/${userUuid}`, {}, false),
      [resource, httpGetJson],
    ),
    addUserToGroup: React.useCallback(
      (userUuid: string, groupUuid: string) =>
        httpPutEmptyResponse(
          `${resource}/${userUuid}/${groupUuid}`,
        ),
      [resource, httpPutEmptyResponse],
    ),
    createUser: React.useCallback(
      (name: string, isGroup: boolean) =>
        httpPostJsonResponse(
          `${resource}/create/${name}/${isGroup}`,
        ),
      [resource, httpPostJsonResponse],
    ),
    deleteUser: React.useCallback(
      (uuid: string) =>
        httpDeleteEmptyResponse(`${resource}/${uuid}`),
      [resource, httpDeleteEmptyResponse],
    ),
    findGroupsForUser: React.useCallback(
      (userUuid: string) =>
        httpGetJson(
          `${resource}/groupsForUser/${userUuid}`,
          {},
          false,
        ),
      [resource, httpGetJson],
    ),
    findUsers: React.useCallback(
      (name?: string, isGroup?: boolean, uuid?: string) => {
        var url = new URL(resource);
        if (name !== undefined && name.length > 0)
          url.searchParams.append("name", name);
        url.searchParams.append("isGroup", (isGroup || false).toString());

        if (uuid !== undefined && uuid.length > 0)
          url.searchParams.append("uuid", uuid);

        return httpGetJson(url.href);
      },
      [resource, httpGetJson],
    ),
    findUsersInGroup: React.useCallback(
      (groupUuid: string) =>
        httpGetJson(
          `${resource}/usersInGroup/${groupUuid}`,
          {},
          false,
        ),
      [resource, httpGetJson],
    ),
    removeUserFromGroup: React.useCallback(
      (userUuid: string, groupUuid: string) =>
        httpDeleteEmptyResponse(
          `${resource}/${userUuid}/${groupUuid}`,
        ),
      [resource, httpDeleteEmptyResponse],
    ),
  };
};

export default useApi;
