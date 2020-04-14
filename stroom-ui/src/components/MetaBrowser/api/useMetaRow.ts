import * as React from "react";

import useApi from "./useApi";
import { MetaRow } from "../types";

const useMetaRow = (metaId: number): MetaRow | undefined => {
  const [dataRow, setDataRow] = React.useState<MetaRow | undefined>(undefined);
  const { getDetailsForSelectedStream } = useApi();

  React.useEffect(() => {
    getDetailsForSelectedStream(metaId).then(setDataRow);
  }, [metaId, setDataRow, getDetailsForSelectedStream]);

  return dataRow;
};

export default useMetaRow;
