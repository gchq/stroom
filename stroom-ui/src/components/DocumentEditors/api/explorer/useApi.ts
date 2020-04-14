import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { SearchProps } from "./types";
import useConfig from "startup/config/useConfig";
import {
  DocRefTree,
  DocRefType,
  DocRefInfoType,
} from "components/DocumentEditors/useDocumentApi/types/base";

const stripDocRef = (docRef: DocRefType) => ({
  uuid: docRef.uuid,
  type: docRef.type,
  name: docRef.name,
});

interface Api {
  fetchDocTree: () => Promise<DocRefTree>;
  fetchDocRefTypes: () => Promise<string[]>;
  fetchDocInfo: (docRef: DocRefType) => Promise<DocRefInfoType>;
  searchApp: (args: SearchProps) => Promise<DocRefType[]>;
  createDocument: (
    docRefType: string,
    docRefName: string,
    destinationFolderRef: DocRefType,
    permissionInheritance: string,
  ) => Promise<DocRefTree>;
  renameDocument: (docRef: DocRefType, name: string) => Promise<DocRefType>;
  copyDocuments: (
    docRefs: DocRefType[],
    destination: DocRefType,
    permissionInheritance: string,
  ) => Promise<DocRefTree>;
  moveDocuments: (
    docRefs: DocRefType[],
    destination: DocRefType,
    permissionInheritance: string,
  ) => Promise<DocRefTree>;
  deleteDocuments: (docRefs: DocRefType[]) => Promise<DocRefTree>;
}

export const useApi = (): Api => {
  const { stroomBaseServiceUrl } = useConfig();

  const {
    httpGetJson,
    httpPostJsonResponse,
    httpPutJsonResponse,
    httpDeleteJsonResponse,
  } = useHttpClient();

  return {
    fetchDocTree: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/explorer/v1/all`),
      [stroomBaseServiceUrl, httpGetJson],
    ),

    fetchDocRefTypes: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/explorer/v1/docRefTypes`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    fetchDocInfo: React.useCallback(
      (docRef: DocRefType) =>
        httpGetJson(
          `${stroomBaseServiceUrl}/explorer/v1/info/${docRef.type}/${
            docRef.uuid
          }`,
        ),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    searchApp: React.useCallback(
      ({ term = "", docRefType = "", pageOffset = 0, pageSize = 10 }) => {
        const params = `searchTerm=${term}&docRefType=${docRefType}&pageOffset=${pageOffset}&pageSize=${pageSize}`;
        const url = `${stroomBaseServiceUrl}/explorer/v1/search?${params}`;

        return httpGetJson(url);
      },
      [stroomBaseServiceUrl, httpGetJson],
    ),
    createDocument: React.useCallback(
      (
        docRefType: string,
        docRefName: string,
        destinationFolderRef: DocRefType,
        permissionInheritance: string,
      ) =>
        httpPostJsonResponse(`${stroomBaseServiceUrl}/explorer/v1/create`, {
          body: JSON.stringify({
            docRefType,
            docRefName,
            destinationFolderRef: stripDocRef(destinationFolderRef),
            permissionInheritance,
          }),
        }),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),
    renameDocument: React.useCallback(
      (docRef: DocRefType, name: string) =>
        httpPutJsonResponse(`${stroomBaseServiceUrl}/explorer/v1/rename`, {
          body: JSON.stringify({
            docRef: stripDocRef(docRef),
            name,
          }),
        }),
      [stroomBaseServiceUrl, httpPutJsonResponse],
    ),
    copyDocuments: React.useCallback(
      (
        docRefs: DocRefType[],
        destination: DocRefType,
        permissionInheritance: string,
      ) =>
        httpPostJsonResponse(`${stroomBaseServiceUrl}/explorer/v1/copy`, {
          body: JSON.stringify({
            docRefs: docRefs.map(stripDocRef),
            destinationFolderRef: stripDocRef(destination),
            permissionInheritance,
          }),
        }),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),
    moveDocuments: React.useCallback(
      (
        docRefs: DocRefType[],
        destination: DocRefType,
        permissionInheritance: string,
      ) =>
        httpPutJsonResponse(`${stroomBaseServiceUrl}/explorer/v1/move`, {
          body: JSON.stringify({
            docRefs: docRefs.map(stripDocRef),
            destinationFolderRef: stripDocRef(destination),
            permissionInheritance,
          }),
        }),
      [stroomBaseServiceUrl, httpPutJsonResponse],
    ),
    deleteDocuments: React.useCallback(
      (docRefs: DocRefType[]) =>
        httpDeleteJsonResponse(`${stroomBaseServiceUrl}/explorer/v1/delete`, {
          body: JSON.stringify(docRefs.map(stripDocRef)),
        }),
      [stroomBaseServiceUrl, httpDeleteJsonResponse],
    ),
  };
};

export default useApi;
