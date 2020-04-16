import * as React from "react";

import useApi from "./useApi";
import { MetaRow } from "../types";

const useMetaRelations = (metaId: number, anyStatus: boolean): MetaRow[] => {
  const [relations, setRelations] = React.useState<MetaRow[]>([]);
  const { getRelations } = useApi();

  React.useEffect(() => {
    getRelations(metaId, anyStatus).then(setRelations);
  }, [metaId, anyStatus, setRelations, getRelations]);

  return relations;
};

export default useMetaRelations;
