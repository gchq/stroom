import * as React from "react";
import DocumentTreeContext, {
  DocumentTreeContextValue,
} from "./DocumentTreeContext";

const useDocumentTree = (): DocumentTreeContextValue => {
  const context = React.useContext(DocumentTreeContext);
  const { fetchDocTree } = context;

  // Ensure the tree has been loaded
  React.useEffect(fetchDocTree, [fetchDocTree]);

  return context;
};

export default useDocumentTree;
