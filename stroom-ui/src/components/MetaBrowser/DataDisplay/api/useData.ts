import * as React from "react";

import useApi from "./useApi";
import { AnyFetchDataResult, UseData, FetchDataParams } from "../types";

const defaultFetchParams: FetchDataParams = {
  pageOffset: 0,
  pageSize: 20,
};

const useData = (metaId: number): UseData => {
  const { getDataForSelectedRow } = useApi();

  const [data, setData] = React.useState<AnyFetchDataResult>(undefined);

  const _getDataForSelectedRow = React.useCallback(() => {
    getDataForSelectedRow({ metaId, ...defaultFetchParams }).then((d) => {
      console.log("D", d);
      // setPagedData({
      //   streamAttributeMaps: d.
      // })
      setData(d);
    });
  }, [metaId, getDataForSelectedRow]);

  React.useEffect(() => {
    _getDataForSelectedRow();
  }, [_getDataForSelectedRow]);

  return {
    data,
    getDataForSelectedRow: _getDataForSelectedRow,
  };
};

export default useData;
