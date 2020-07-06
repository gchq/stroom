import * as React from "react";

import { PropsWithChildren, ReactElement } from "react";
import {
  useTable,
  useBlockLayout,
  useResizeColumns,
  useRowSelect,
} from "react-table";
import { useSticky } from "react-table-sticky";

export interface TableProps<T> {
  columns: any[];
  data: T[];
  onSelect?: (selected: T[]) => void;
  onDoubleSelect?: (selected: T[]) => void;
}

export const Table = <T,>(
  props: PropsWithChildren<TableProps<T>>,
): ReactElement<any, any> | null => {
  const {
    columns,
    data,
    onSelect = () => undefined,
    onDoubleSelect = () => undefined,
  } = props;

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
      autoResetSelectedRows: false,
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
            key={headerGroup.id}
            {...headerGroup.getHeaderGroupProps()}
            className="tr"
          >
            {headerGroup.headers.map((column) => (
              <div key={column.id} {...column.getHeaderProps()} className="th">
                <div className="cell">{column.render("Header")}</div>
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
              key={row.id}
              {...row.getRowProps()}
              className={`tr ${row.isSelected ? "selected" : ""}`}
              onClick={() => {
                if (!row.isSelected) {
                  toggleAllRowsSelected(false);
                  row.toggleRowSelected();
                }
                onSelect([row.original]);
              }}
              onDoubleClick={() => {
                if (!row.isSelected) {
                  toggleAllRowsSelected(false);
                  row.toggleRowSelected();
                }
                onDoubleSelect([row.original]);
              }}
            >
              {row.cells.map((cell) => (
                <div key={cell.id} {...cell.getCellProps()} className="td">
                  <div className="cell">{cell.render("Cell")}</div>
                </div>
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
};
