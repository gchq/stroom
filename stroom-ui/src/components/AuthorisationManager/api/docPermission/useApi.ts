import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { DocumentPermissions } from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface Api {
  // Server Side Constant
  getPermissionForDocType: (docRefType: string) => Promise<string[]>;

  // By Doc and User
  getPermissionsForDocumentForUser: (
    docRefUuid: string,
    userUuid: string,
  ) => Promise<string[]>;
  addDocPermission: (
    docRefUuid: string,
    userUuid: string,
    permissionName: string,
  ) => Promise<void>;
  removeDocPermission: (
    docRefUuid: string,
    userUuid: string,
    permissionName: string,
  ) => Promise<void>;

  // By Doc
  getPermissionForDoc: (docRefUuid: string) => Promise<DocumentPermissions>;
  clearDocPermissionsForUser: (
    docRefUuid: string,
    userUuid: string,
  ) => Promise<void>;
  clearDocPermissions: (docRefUuid: string) => Promise<void>;
}

export const useApi = (): Api => {
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/docPermissions/v1");

  const {
    httpGetJson,
    httpPostEmptyResponse,
    httpDeleteEmptyResponse,
  } = useHttpClient();

  return {
    getPermissionForDocType: React.useCallback(
      (docRefType: string): Promise<string[]> =>
        httpGetJson(
          `${resource}/forDocType/${docRefType}`,
          {},
          false,
        ),
      [resource, httpGetJson],
    ),
    getPermissionsForDocumentForUser: React.useCallback(
      (docRefUuid: string, userUuid: string): Promise<string[]> =>
        httpGetJson(
          `${resource}/forDocForUser/${docRefUuid}/${userUuid}`,
        ),
      [resource, httpGetJson],
    ),
    addDocPermission: React.useCallback(
      (
        docRefUuid: string,
        userUuid: string,
        permissionName: string,
      ): Promise<void> =>
        httpPostEmptyResponse(
          `${resource}/forDocForUser/${docRefUuid}/${userUuid}/${permissionName}`,
        ),
      [resource, httpPostEmptyResponse],
    ),
    removeDocPermission: React.useCallback(
      (
        docRefUuid: string,
        userUuid: string,
        permissionName: string,
      ): Promise<void> =>
        httpDeleteEmptyResponse(
          `${resource}/forDocForUser/${docRefUuid}/${userUuid}/${permissionName}`,
        ),
      [resource, httpDeleteEmptyResponse],
    ),
    getPermissionForDoc: React.useCallback(
      (docRefUuid: string) =>
        httpGetJson(
          `${resource}/forDoc/${docRefUuid}`,
        ),
      [resource, httpGetJson],
    ),
    clearDocPermissionsForUser: React.useCallback(
      (docRefUuid: string, userUuid: string) =>
        httpDeleteEmptyResponse(
          `${resource}/forDocForUser/${docRefUuid}/${userUuid}`,
        ),
      [resource, httpDeleteEmptyResponse],
    ),
    clearDocPermissions: React.useCallback(
      (docRefUuid: string) =>
        httpDeleteEmptyResponse(
          `${resource}/forDoc/${docRefUuid}`,
        ),
      [resource, httpDeleteEmptyResponse],
    ),
  };
};

export default useApi;
