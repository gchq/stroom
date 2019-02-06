import * as React from "react";

import { path } from "ramda";
import ReactTable, { RowInfo } from "react-table";

import { IndexVolume } from "../../types";

export interface Props {
  indexVolumes: Array<IndexVolume>;
  selectedIndexVolume?: IndexVolume;
  onSelection: (id?: number) => void;
}

const COLUMNS = [
  {
    id: "id",
    Header: "ID",
    accessor: (u: IndexVolume) => u.id
  },
  {
    id: "nodeName",
    Header: "Node",
    accessor: (u: IndexVolume) => u.nodeName
  },
  {
    id: "path",
    Header: "Path",
    accessor: (u: IndexVolume) => u.path
  }
];

const IndexVolumesTable = ({
  indexVolumes,
  selectedIndexVolume,
  onSelection
}: Props) => (
  <ReactTable
    data={indexVolumes}
    columns={COLUMNS}
    getTdProps={(state: any, rowInfo: RowInfo) => {
      return {
        onClick: (_: any, handleOriginal: () => void) => {
          if (rowInfo !== undefined) {
            if (
              !!selectedIndexVolume &&
              selectedIndexVolume.id === rowInfo.original.id
            ) {
              onSelection();
            } else {
              onSelection(rowInfo.original.id);
            }
          }

          if (handleOriginal) {
            handleOriginal();
          }
        }
      };
    }}
    getTrProps={(_: any, rowInfo: RowInfo) => {
      // We don't want to see a hover on a row without data.
      // If a row is selected we want to see the selected color.
      const isSelected =
        !!selectedIndexVolume &&
        path(["original", "id"], rowInfo) === selectedIndexVolume.id;
      const hasData = path(["original", "id"], rowInfo) !== undefined;
      let className;
      if (hasData) {
        className = isSelected ? "selected hoverable" : "hoverable";
      }
      return {
        className
      };
    }}
  />
);

export default IndexVolumesTable;
