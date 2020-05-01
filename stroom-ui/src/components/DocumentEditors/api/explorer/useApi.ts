import * as React from "react";

import useHttpClient from "lib/useHttpClient";
import { SearchProps } from "./types";
import { DocRefInfoType, DocRefTree, DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import useUrlFactory from "lib/useUrlFactory";

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
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/explorer/v1");

  const {
    httpGetJson,
    httpPostJsonResponse,
    httpPutJsonResponse,
    httpDeleteJsonResponse,
  } = useHttpClient();

  return {
    fetchDocTree: React.useCallback(
      () => httpGetJson(`${resource}/all`),
      [resource, httpGetJson],
    ),

    fetchDocRefTypes: React.useCallback(
      () => httpGetJson(`${resource}/docRefTypes`),
      [resource, httpGetJson],
    ),
    fetchDocInfo: React.useCallback(
      (docRef: DocRefType) =>
        httpGetJson(`${resource}/info/${docRef.type}/${docRef.uuid}`),
      [resource, httpGetJson],
    ),
    searchApp: React.useCallback(
      ({ term = "", docRefType = "", pageOffset = 0, pageSize = 10 }) => {
        const params = `searchTerm=${term}&docRefType=${docRefType}&pageOffset=${pageOffset}&pageSize=${pageSize}`;
        const url = `${resource}/search?${params}`;

        return httpGetJson(url);
      },
      [resource, httpGetJson],
    ),
    createDocument: React.useCallback(
      (
        docRefType: string,
        docRefName: string,
        destinationFolderRef: DocRefType,
        permissionInheritance: string,
      ) =>
        httpPostJsonResponse(`${resource}/create`, {
          body: JSON.stringify({
            docRefType,
            docRefName,
            destinationFolderRef: stripDocRef(destinationFolderRef),
            permissionInheritance,
          }),
        }),
      [resource, httpPostJsonResponse],
    ),
    renameDocument: React.useCallback(
      (docRef: DocRefType, name: string) =>
        httpPutJsonResponse(`${resource}/rename`, {
          body: JSON.stringify({
            docRef: stripDocRef(docRef),
            name,
          }),
        }),
      [resource, httpPutJsonResponse],
    ),
    copyDocuments: React.useCallback(
      (
        docRefs: DocRefType[],
        destination: DocRefType,
        permissionInheritance: string,
      ) =>
        httpPostJsonResponse(`${resource}/copy`, {
          body: JSON.stringify({
            docRefs: docRefs.map(stripDocRef),
            destinationFolderRef: stripDocRef(destination),
            permissionInheritance,
          }),
        }),
      [resource, httpPostJsonResponse],
    ),
    moveDocuments: React.useCallback(
      (
        docRefs: DocRefType[],
        destination: DocRefType,
        permissionInheritance: string,
      ) =>
        httpPutJsonResponse(`${resource}/v1/move`, {
          body: JSON.stringify({
            docRefs: docRefs.map(stripDocRef),
            destinationFolderRef: stripDocRef(destination),
            permissionInheritance,
          }),
        }),
      [resource, httpPutJsonResponse],
    ),
    deleteDocuments: React.useCallback(
      (docRefs: DocRefType[]) =>
        httpDeleteJsonResponse(`${resource}/delete`, {
          body: JSON.stringify(docRefs.map(stripDocRef)),
        }),
      [resource, httpDeleteJsonResponse],
    ),
  };
};

export default useApi;
