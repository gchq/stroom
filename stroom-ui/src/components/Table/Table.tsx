import * as React from "react";

import { FunctionComponent } from "react";
import {
  useTable,
  useBlockLayout,
  useResizeColumns,
  useRowSelect,
} from "react-table";
import { useSticky } from "react-table-sticky";

export interface TableProps {
  columns: any[];
  data: any[];
  onSelect?: (selected: any[]) => void;
}

export const Table: FunctionComponent<TableProps> = ({
  columns,
  data,
  onSelect = () => undefined,
}) => {
  const defaultColumn = React.useMemo(
    () => ({
      minWidth: 30,
      width: 150,
      maxWidth: 400,
    }),
    [],
  );

  const {
    getTableProps,
    getTableBodyProps,
    headerGroups,
    rows,
    prepareRow,
    toggleAllRowsSelected,
  } = useTable(
    {
      columns,
      data,
      defaultColumn,
      initialState: {
        hiddenColumns: ["id"],
      },
    },
    useBlockLayout,
    useResizeColumns,
    useSticky,
    useRowSelect,
  );

  return (
    <div
      {...getTableProps()}
      className="table sticky w-100 h-100"
      // style={{ width: 300, height: 300 }}
    >
      <div className="header">
        {headerGroups.map((headerGroup) => (
          <div
            key={headerGroup}
            {...headerGroup.getHeaderGroupProps()}
            className="tr"
          >
            {headerGroup.headers.map((column) => (
              <div key={column} {...column.getHeaderProps()} className="th">
                {column.render("Header")}
                <div
                  {...column.getResizerProps()}
                  className={`resizer ${column.isResizing ? "isResizing" : ""}`}
                />
              </div>
            ))}
          </div>
        ))}
      </div>
      <div {...getTableBodyProps()} className="body">
        {rows.map((row) => {
          prepareRow(row);
          return (
            <div
              key={row}
              {...row.getRowProps()}
              className={`tr ${row.isSelected ? "selected" : ""}`}
              onClick={() => {
                toggleAllRowsSelected(false);
                row.toggleRowSelected();
                onSelect(row.id);
              }}
            >
              {row.cells.map((cell) => (
                <div key={cell} {...cell.getCellProps()} className="td">
                  {cell.render("Cell")}
                </div>
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default Table;
