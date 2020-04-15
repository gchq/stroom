import {
  DocRefTree,
  DocRefWithLineage,
} from "components/DocumentEditors/useDocumentApi/types/base";

export const DEFAULT_TREE: DocRefTree = {
  uuid: "NONE",
  type: "NONE",
  name: "NONE",
};

export const DEFAULT_DOC_REF_WITH_LINEAGE: DocRefWithLineage = {
  lineage: [],
  node: DEFAULT_TREE,
};
