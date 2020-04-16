import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { DocumentPermissions } from "./types";
import useConfig from "startup/config/useConfig";

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
  const { stroomBaseServiceUrl } = useConfig();

  const {
    httpGetJson,
    httpPostEmptyResponse,
    httpDeleteEmptyResponse,
  } = useHttpClient();

  return {
    getPermissionForDocType: React.useCallback(
      (docRefType: string): Promise<string[]> =>
        httpGetJson(
          `${stroomBaseServiceUrl}/docPermissions/v1/forDocType/${docRefType}`,
          {},
          false,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    getPermissionsForDocumentForUser: React.useCallback(
      (docRefUuid: string, userUuid: string): Promise<string[]> =>
        httpGetJson(
          `${stroomBaseServiceUrl}/docPermissions/v1/forDocForUser/${docRefUuid}/${userUuid}`,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    addDocPermission: React.useCallback(
      (
        docRefUuid: string,
        userUuid: string,
        permissionName: string,
      ): Promise<void> =>
        httpPostEmptyResponse(
          `${stroomBaseServiceUrl}/docPermissions/v1/forDocForUser/${docRefUuid}/${userUuid}/${permissionName}`,
        ),
      [stroomBaseServiceUrl, httpPostEmptyResponse],
    ),
    removeDocPermission: React.useCallback(
      (
        docRefUuid: string,
        userUuid: string,
        permissionName: string,
      ): Promise<void> =>
        httpDeleteEmptyResponse(
          `${stroomBaseServiceUrl}/docPermissions/v1/forDocForUser/${docRefUuid}/${userUuid}/${permissionName}`,
        ),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
    getPermissionForDoc: React.useCallback(
      (docRefUuid: string) =>
        httpGetJson(
          `${stroomBaseServiceUrl}/docPermissions/v1/forDoc/${docRefUuid}`,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    clearDocPermissionsForUser: React.useCallback(
      (docRefUuid: string, userUuid: string) =>
        httpDeleteEmptyResponse(
          `${stroomBaseServiceUrl}/docPermissions/v1/forDocForUser/${docRefUuid}/${userUuid}`,
        ),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
    clearDocPermissions: React.useCallback(
      (docRefUuid: string) =>
        httpDeleteEmptyResponse(
          `${stroomBaseServiceUrl}/docPermissions/v1/forDoc/${docRefUuid}`,
        ),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
  };
};

export default useApi;
