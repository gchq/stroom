import * as React from "react";
import {
  DocRefType,
  DocRefTree,
} from "components/DocumentEditors/useDocumentApi/types/base";
import DocumentTreeContext from "./DocumentTreeContext";
import { SearchProps } from "./types";

interface UseDocumentSearch {
  documentTree: DocRefTree;
  searchResults: DocRefType[];
  searchApp: (args: SearchProps) => void;
}

const useDocumentSearch = (): UseDocumentSearch => {
  const { searchApp, fetchDocTree, documentTree } = React.useContext(
    DocumentTreeContext,
  );

  const [searchResults, setSearchResults] = React.useState<DocRefType[]>([]);

  React.useEffect(fetchDocTree, [fetchDocTree]);

  return {
    documentTree,
    searchResults,
    searchApp: React.useCallback(p => searchApp(p).then(setSearchResults), [
      searchApp,
      setSearchResults,
    ]),
  };
};

export default useDocumentSearch;
