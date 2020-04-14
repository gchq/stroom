import * as React from "react";
import useDocumentTree from "./useDocumentTree";
import { DocRefWithLineage } from "components/DocumentEditors/useDocumentApi/types/base";

const useDocument = (docRefUuid: string): DocRefWithLineage | undefined => {
  const { findDocRefWithLineage } = useDocumentTree();

  return React.useMemo(() => findDocRefWithLineage(docRefUuid), [
    findDocRefWithLineage,
    docRefUuid,
  ]);
};

export default useDocument;
