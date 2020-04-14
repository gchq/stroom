import * as React from "react";

import useApi from "./useApi";
import {
  DocRefType,
  DocRefInfoType,
} from "components/DocumentEditors/useDocumentApi/types/base";

const useDocRefInfo = (docRef?: DocRefType): DocRefInfoType | undefined => {
  const [docRefInfo, setDocRefInfo] = React.useState<
    DocRefInfoType | undefined
  >(undefined);
  const { fetchDocInfo } = useApi();
  React.useEffect(() => {
    if (!!docRef) {
      fetchDocInfo(docRef).then(setDocRefInfo);
    }
  }, [docRef, fetchDocInfo, setDocRefInfo]);

  return docRefInfo;
};

export default useDocRefInfo;
