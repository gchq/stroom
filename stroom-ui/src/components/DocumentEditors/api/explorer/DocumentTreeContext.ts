import * as React from "react";

import { SearchProps } from "./types";
import { DEFAULT_TREE, DEFAULT_DOC_REF_WITH_LINEAGE } from "./values";
import {
  DocRefTree,
  DocRefType,
  DocRefWithLineage,
} from "components/DocumentEditors/useDocumentApi/types/base";

export interface DocumentTreeContextValue {
  documentTree: DocRefTree;
  fetchDocTree: () => void;
  searchApp: (args: SearchProps) => Promise<DocRefType[]>;
  createDocument: (
    docRefType: string,
    docRefName: string,
    destinationFolderRef: DocRefType,
    permissionInheritance: string,
  ) => void;
  renameDocument: (docRef: DocRefType, name: string) => void;
  copyDocuments: (
    uuids: string[],
    destination: DocRefType,
    permissionInheritance: string,
  ) => void;
  moveDocuments: (
    uuids: string[],
    destination: DocRefType,
    permissionInheritance: string,
  ) => void;
  deleteDocuments: (uuids: string[]) => void;
  findDocRefWithLineage: (docRefUuid: string) => DocRefWithLineage;
}

const NO_OP_FUNCTION = () =>
  console.error("Unexpected call to default context");

const DocumentTreeContext = React.createContext<DocumentTreeContextValue>({
  fetchDocTree: NO_OP_FUNCTION,
  copyDocuments: NO_OP_FUNCTION,
  createDocument: NO_OP_FUNCTION,
  deleteDocuments: NO_OP_FUNCTION,
  documentTree: DEFAULT_TREE,
  findDocRefWithLineage: () => DEFAULT_DOC_REF_WITH_LINEAGE,
  moveDocuments: NO_OP_FUNCTION,
  renameDocument: NO_OP_FUNCTION,
  searchApp: () => Promise.reject(),
});

export default DocumentTreeContext;
