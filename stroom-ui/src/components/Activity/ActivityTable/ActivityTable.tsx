import * as React from "react";

import ReactTable, { TableProps } from "react-table";

import {
  useSelectableReactTable,
  SelectionBehaviour,
} from "lib/useSelectableItemListing";
import { TableOutProps } from "lib/useSelectableItemListing/types";
import { Activity } from "../api/types";

interface Props {
  activities: Activity[];
  selectableTableProps: TableOutProps<Activity>;
}

interface Row {
  value: Activity;
}

const ActivityCell: React.FunctionComponent<Row> = ({ value: activity }) => {
  return (
    <div className="ActivityTable__row">
      {activity &&
        activity.details &&
        activity.details.properties &&
        activity.details.properties
          .filter(({ showInList }) => showInList)
          .map(({ name, value }, i: number) => {
            return (
              <div className="ActivityTable__row-detail" key={i}>
                <b>{name}: </b>
                {value}
              </div>
            );
          })}
    </div>
  );
};

const COLUMNS = [
  {
    id: "activity",
    Header: "Activity",
    accessor: (u: Activity) => u,
    Cell: ActivityCell,
  },
];

const ActivityTable: React.FunctionComponent<Props> = ({
  selectableTableProps: { onKeyDown, tableProps },
}) => (
  <div className="fill-space" tabIndex={0} onKeyDown={onKeyDown}>
    <ReactTable className="fill-space -striped -highlight" {...tableProps} />
  </div>
);

interface UseTable {
  componentProps: Props;
}

export const useTable = (
  activities: Activity[],
  customTableProps?: Partial<TableProps>,
): UseTable => {
  const selectableTableProps = useSelectableReactTable<Activity>(
    {
      getKey: React.useCallback(v => v.id, []),
      items: activities,
      selectionBehaviour: SelectionBehaviour.SINGLE,
    },
    {
      columns: COLUMNS,
      ...customTableProps,
    },
  );

  return {
    componentProps: {
      selectableTableProps,
      activities,
    },
  };
};

export default ActivityTable;
