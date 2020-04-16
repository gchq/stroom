import * as React from "react";

import { UseStreamSearch } from "./types";
import useApi from "./useApi";
import { ExpressionOperatorType } from "components/ExpressionBuilder/types";
import { StreamAttributeMapResult, PageRequest } from "../types";

const defaultStreams: StreamAttributeMapResult = {
  streamAttributeMaps: [],
  pageResponse: {
    offset: 0,
    total: 0,
    length: 0,
    exact: true,
  },
};

const useMetaSearch = (): UseStreamSearch => {
  const [streams, setStreams] = React.useState<StreamAttributeMapResult>(
    defaultStreams,
  );
  const { fetch, search } = useApi();

  return {
    streams,
    search: React.useCallback(
      (e: ExpressionOperatorType, p: PageRequest) =>
        search(e, p).then(setStreams),
      [search, setStreams],
    ),
    fetch: React.useCallback((s: PageRequest) => fetch(s).then(setStreams), [
      fetch,
      setStreams,
    ]),
  };
};

export default useMetaSearch;
