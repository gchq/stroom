import * as React from "react";
import { MetaRow } from "../types";
import useMetaRelations from "../api/useMetaRelations";
import MetaTable, { useTable } from "../MetaTable";

interface Props {
  metaRow: MetaRow;
}

const MetaRelations: React.FunctionComponent<Props> = ({ metaRow }) => {
  const relatedRows: MetaRow[] = useMetaRelations(metaRow.meta.id, true);

  const tableProps = useTable({
    pageResponse: {
      offset: 0,
      length: relatedRows.length,
      total: relatedRows.length,
      exact: true,
    },
    streamAttributeMaps: relatedRows,
  });

  return <MetaTable {...tableProps} />;;
};

export default MetaRelations;
