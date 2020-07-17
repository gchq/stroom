import * as React from "react";

import { PropsWithChildren, ReactElement, useEffect, useMemo } from "react";
import {
  useTable,
  useBlockLayout,
  useResizeColumns,
  useRowSelect,
  useSortBy,
} from "react-table";
import { useSticky } from "react-table-sticky";
import { Sort } from "../Account/api/types";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

export interface TableSort {
  id: string;
  desc?: boolean;
}

export interface TableProps<T> {
  columns: any[];
  data: T[];
  initialSortBy?: Sort[];
  onChangeSort?: (sort: Sort[]) => void;
  onSelect?: (selected: T[]) => void;
  onDoubleSelect?: (selected: T[]) => void;
}

export const Table = <T,>(
  props: PropsWithChildren<TableProps<T>>,
): ReactElement<any, any> | null => {
  const {
    columns,
    data,
    initialSortBy,
    onChangeSort,
    onSelect = () => undefined,
    onDoubleSelect = () => undefined,
  } = props;

  const defaultColumn = useMemo(
    () => ({
      minWidth: 30,
      width: 150,
      maxWidth: 400,
    }),
    [],
  );

  // Convert the supplied initial sort object to a React Table sort array.
  const initialSort: TableSort[] = useMemo(() => {
    if (!initialSortBy) {
      return undefined;
    }
    return initialSortBy.map((i) => {
      return {
        id: i.field,
        desc: i.direction === "DESCENDING",
      };
    });
  }, [initialSortBy]);

  const initialState = useMemo(() => {
    return {
      hiddenColumns: ["id"],
      sortBy: initialSort,
    };
  }, [initialSort]);

  const {
    getTableProps,
    getTableBodyProps,
    headerGroups,
    rows,
    prepareRow,
    state: { sortBy },
    toggleAllRowsSelected,
  } = useTable(
    {
      columns,
      data,
      defaultColumn,
      initialState,
      autoResetSelectedRows: false,
      manualSorting: true,
    },
    useBlockLayout,
    useResizeColumns,
    useSticky,
    useSortBy,
    useRowSelect,
  );

  useEffect(() => {
    if (sortBy !== initialSort) {
      // Convert the table sort into our sort object.
      const sort: Sort[] = sortBy.map((i) => {
        return {
          field: i.id,
          direction: i.desc ? "DESCENDING" : "ASCENDING",
        };
      });

      onChangeSort(sort);
    }
  }, [sortBy]);

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
              <div
                key={column.id}
                {...column.getHeaderProps(column.getSortByToggleProps())}
                className="th"
              >
                <div className="cell">
                  {column.render("Header")}
                  {column.isSorted ? (
                    column.isSortedDesc ? (
                      <FontAwesomeIcon icon={"sort-alpha-down-alt"} size="lg" />
                    ) : (
                      <FontAwesomeIcon icon={"sort-alpha-down"} size="lg" />
                    )
                  ) : (
                    ""
                  )}
                </div>
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
