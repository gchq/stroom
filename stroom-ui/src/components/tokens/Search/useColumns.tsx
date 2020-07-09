import * as React from "react";
import moment from "moment";
import { Column, ReactTableFunction, RowInfo } from "react-table";
import { Token } from "../api/types";
import useConfig from "startup/config/useConfig";

moment.updateLocale("en", {
  invalidDate: "No date",
});

/** There is a corresponding react-table type but doing it like this is neater. */
interface FilterProps {
  filter: any;
  onChange: ReactTableFunction;
}

const useColumns = (
  selectedTokenRowId: string | undefined,
  setEnabledStateOnToken: (tokenId: number, enabled: boolean) => any,
): Column<Token>[] => {
  const {
    uiPreferences: { dateFormat },
  } = useConfig();
  const getEnabledCellFilter = React.useCallback(
    ({ filter, onChange }: FilterProps) => {
      return (
        <select
          onChange={(event) => onChange(event.target.value)}
          style={{ width: "100%" }}
          value={filter ? filter.value : "all"}
        >
          <option value="">Show all</option>
          <option value="true">Enabled only</option>
          <option value="false">Disabled only</option>
        </select>
      );
    },
    [],
  );

  const EnabledCell: React.FunctionComponent<RowInfo> = React.useCallback(
    ({ row }) => {
      const state = row.enabled;
      const tokenId = row.id;
      return (
        <div className="TokenSearch__table__checkbox">
          <input
            type="checkbox"
            checked={state}
            onChange={() => setEnabledStateOnToken(tokenId, !state)}
          />
        </div>
      );
    },
    [setEnabledStateOnToken],
  );

  return [
    {
      Header: "",
      accessor: "id",
      Cell: (row: RowInfo): React.ReactNode => {
        const selectionDiv = () => (
          <div>
            {selectedTokenRowId === row.row.value ? "selected" : "unselected"}
          </div>
        );
        selectionDiv.displayName = "selectionDiv";
        return selectionDiv;
      },
      filterable: false,
      show: false,
    },
    {
      Header: "User",
      accessor: "userEmail",
      maxWidth: 190,
    },
    {
      Header: "Enabled",
      accessor: "enabled",
      maxWidth: 80,
      Cell: EnabledCell,
      Filter: getEnabledCellFilter,
    },
    {
      Header: "Expires on",
      accessor: "expiresOn",
      Cell: (row: RowInfo) => moment(row.row.expiresOn).format(dateFormat),
      filterable: false,
      maxWidth: 205,
    },
    {
      Header: "Issued on",
      accessor: "issuedOn",
      Cell: (row: RowInfo) => moment(row.row.issuedOn).format(dateFormat),
      filterable: false,
      maxWidth: 205,
    },
  ] as Column[];
};

export default useColumns;
