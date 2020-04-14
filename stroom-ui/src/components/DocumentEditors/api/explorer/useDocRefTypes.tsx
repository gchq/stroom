import * as React from "react";

import useApi from "./useApi";

const useDocRefTypes = (): string[] => {
  const [docRefTypes, setDocRefTypes] = React.useState<string[]>([]);

  const { fetchDocRefTypes } = useApi();

  React.useEffect(() => {
    fetchDocRefTypes().then(setDocRefTypes);
  }, [setDocRefTypes, fetchDocRefTypes]);

  return docRefTypes;
};

export default useDocRefTypes;
