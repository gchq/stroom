import * as React from "react";

import { DOCUMENT_RESOURCES, ResourcesByDocType } from "./types/resourceUrls";
import useHttpClient from "lib/useHttpClient";
import { DocumentBase } from "./types/base";
import { DocumentApi } from "./types/documentApi";
import cogoToast from "cogo-toast";
import useUrlFactory from "lib/useUrlFactory";

/**
 * This returns an API that can fetch/save a particular document type.
 * The specific types supported and their respective resource URL's can be found under ./types.
 * This class is based on a certain common appearance for document resources.
 * Any documents which do not expose an interface like this will require a bespoke API + useHook combination.
 *
 * @param docRefType The doc ref type to retrieve/save
 */
const useDocumentApi = <T extends keyof ResourcesByDocType,
  D extends DocumentBase<T>>(
  docRefType: T,
): DocumentApi<D> => {
  const { apiUrl } = useUrlFactory();
  const { httpGetJson, httpPostEmptyResponse } = useHttpClient();
  const resourcePath = DOCUMENT_RESOURCES[docRefType];
  const resource = apiUrl(resourcePath);

  const fetchDocument = React.useCallback(
    (docRefUuid: string) =>
      httpGetJson(`${resource}${docRefUuid}`),
    [resource, httpGetJson],
  );
  const saveDocument = React.useCallback(
    (docRefContents: D) =>
      httpPostEmptyResponse(
        `${resource}${docRefContents.uuid}`,
        {
          body: JSON.stringify(docRefContents),
        },
      ).then(() => cogoToast.info(`Document Saved ${docRefType}`)),
    [docRefType, resource, httpPostEmptyResponse],
  );

  if (!resourcePath) {
    throw new Error(
      `API for Doc Ref requested, no generic implementation ${docRefType}`,
    );
  }

  return {
    fetchDocument,
    saveDocument,
  };
};

export default useDocumentApi;
