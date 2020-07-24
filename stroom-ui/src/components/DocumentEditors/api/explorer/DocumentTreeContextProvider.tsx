import * as React from "react";

import useApi from "./useApi";
import {
  updateItemInTree,
  findItem,
  findByUuids,
} from "lib/treeUtils/treeUtils";
import DocumentTreeContext, {
  DocumentTreeContextValue,
} from "./DocumentTreeContext";
import { DEFAULT_TREE, DEFAULT_DOC_REF_WITH_LINEAGE } from "./values";
import {
  DocRefTree,
  DocRefType,
} from "components/DocumentEditors/useDocumentApi/types/base";

const DocumentTreeContextProvider: React.FunctionComponent = ({ children }) => {
  const [documentTree, setDocumentTree] = React.useState<DocRefTree>(
    DEFAULT_TREE,
  );

  const {
    fetchDocTree,
    createDocument,
    copyDocuments,
    deleteDocuments,
    renameDocument,
    moveDocuments,
    searchApp,
  } = useApi();

  const contextValue: DocumentTreeContextValue = {
    documentTree,
    searchApp,
    fetchDocTree: React.useCallback(() => {
      if (documentTree === DEFAULT_TREE) {
        fetchDocTree().then(setDocumentTree);
      }
    }, [documentTree, fetchDocTree]),
    createDocument: React.useCallback(
      (
        docRefType: string,
        docRefName: string,
        destinationFolderRef: DocRefType,
        permissionInheritance: string,
      ) => {
        createDocument(
          docRefType,
          docRefName,
          destinationFolderRef,
          permissionInheritance,
        ).then(setDocumentTree);
      },
      [createDocument, setDocumentTree],
    ),
    renameDocument: React.useCallback(
      (docRef: DocRefType, name: string) => {
        renameDocument(docRef, name).then((resultDocRef) => {
          const newTree = updateItemInTree(
            documentTree,
            docRef.uuid,
            resultDocRef,
          );
          setDocumentTree(newTree);
        });
      },
      [renameDocument, setDocumentTree, documentTree],
    ),
    copyDocuments: React.useCallback(
      (
        uuids: string[],
        destination: DocRefType,
        permissionInheritance: string,
      ) => {
        const docRefs = findByUuids(documentTree, uuids);
        copyDocuments(docRefs, destination, permissionInheritance).then(
          setDocumentTree,
        );
      },
      [documentTree, copyDocuments, setDocumentTree],
    ),
    moveDocuments: React.useCallback(
      (
        uuids: string[],
        destination: DocRefType,
        permissionInheritance: string,
      ) => {
        const docRefs = findByUuids(documentTree, uuids);
        moveDocuments(docRefs, destination, permissionInheritance).then(
          setDocumentTree,
        );
      },
      [documentTree, moveDocuments, setDocumentTree],
    ),
    deleteDocuments: React.useCallback(
      (uuids: string[]) => {
        const docRefs = findByUuids(documentTree, uuids);
        deleteDocuments(docRefs).then(setDocumentTree);
      },
      [documentTree, deleteDocuments, setDocumentTree],
    ),
    findDocRefWithLineage: React.useCallback(
      (docRefUuid: string) =>
        findItem(documentTree, docRefUuid) || DEFAULT_DOC_REF_WITH_LINEAGE,
      [documentTree],
    ),
  };

  return (
    <DocumentTreeContext.Provider value={contextValue}>
      {children}
    </DocumentTreeContext.Provider>
  );
};

export default DocumentTreeContextProvider;
