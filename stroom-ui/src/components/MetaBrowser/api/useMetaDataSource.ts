import useApi from "./useApi";
import * as React from "react";
import { DataSourceType } from "components/ExpressionBuilder/types";

const defaultDataSource: DataSourceType = {
  fields: [],
};

const useMetaDataSource = (): DataSourceType => {
  const [dataSource, setDataSource] = React.useState<
    DataSourceType | undefined
  >(undefined);
  const { fetchDataSource } = useApi();

  React.useEffect(() => {
    fetchDataSource().then(setDataSource);
  }, [fetchDataSource, setDataSource]);

  return dataSource || defaultDataSource;
};

export default useMetaDataSource;
