import * as React from "react";
import { ReactTableFunction, RowInfo, Column } from "react-table";
import StateCell from "./StateCell";
import { Account } from "../types";
import * as moment from "moment";
import useConfig from "startup/config/useConfig";

moment.updateLocale("en", {
  invalidDate: "No date",
});

/** There is a corresponding react-table type but doing it like this is neater. */
interface FilterProps {
  filter: any;
  onChange: ReactTableFunction;
}

const useColumns = (selectedUserRowId: string | undefined): Column<Account>[] => {
  const { dateFormat } = useConfig();
  const IdCell: React.FunctionComponent<RowInfo> = React.useCallback(
    ({ row }) => (
      <div>
        {selectedUserRowId === row.row.value ? "selected" : "unselected"}
      </div>
    ),
    [selectedUserRowId],
  );

  const getStateCellFilter = React.useCallback(
    ({ filter, onChange }: FilterProps) => {
      return (
        <select
          onChange={event => onChange(event.target.value)}
          style={{ width: "100%" }}
          value={filter ? filter.value : "all"}
        >
          <option value="">Show all</option>
          <option value="enabled">Active only</option>
          <option value="locked">Locked only</option>
          <option value="disabled">Inactive only</option>
        </select>
      );
    },
    [],
  );

  const filterRow = React.useCallback((row: any, filter: any) => {
    var index = row[filter.id]
      .toLowerCase()
      .indexOf(filter.value.toLowerCase());
    return index >= 0;
  }, []);

  return [
    {
      Header: "",
      accessor: "id",
      Cell: IdCell,
      filterable: false,
      show: false,
    },
    {
      Header: "Email",
      accessor: "email",
      maxWidth: 190,
      filterMethod: (filter: any, row: any) => filterRow(row, filter),
    },
    {
      Header: "Account status",
      accessor: "state",
      maxWidth: 100,
      Cell: StateCell,
      Filter: getStateCellFilter,
    },
    {
      Header: "Last Sign In",
      accessor: "last_login",
      Cell: (row: RowInfo) => moment(row.row.value).format(dateFormat),
      maxWidth: 205,
      filterable: false,
    },
    {
      Header: "Sign In failures",
      accessor: "login_failures",
      maxWidth: 100,
    },
    {
      Header: "Comments",
      accessor: "comments",
      filterMethod: (filter: any, row: any) => filterRow(row, filter),
    },
  ];
};

export default useColumns;
