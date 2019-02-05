import * as React from "react";

import { path } from "ramda";
import ReactTable, { RowInfo } from "react-table";

import { IndexVolumeGroup } from "../../types";

export interface Props {
  groups: Array<IndexVolumeGroup>;
  selectedGroup?: IndexVolumeGroup;
  onSelection: (name?: string) => void;
}

const INDEX_VOLUME_GROUP_COLUMNS = [
  {
    id: "name",
    Header: "Name",
    accessor: (u: IndexVolumeGroup) => u.name
  }
];

const IndexVolumeGroupsTable = ({
  groups,
  selectedGroup,
  onSelection
}: Props) => (
  <ReactTable
    data={groups}
    columns={INDEX_VOLUME_GROUP_COLUMNS}
    getTdProps={(state: any, rowInfo: RowInfo) => {
      return {
        onClick: (_: any, handleOriginal: () => void) => {
          if (rowInfo !== undefined) {
            if (
              !!selectedGroup &&
              selectedGroup.name === rowInfo.original.name
            ) {
              onSelection();
            } else {
              onSelection(rowInfo.original.name);
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
        !!selectedGroup &&
        path(["original", "name"], rowInfo) === selectedGroup.name;
      const hasData = path(["original", "name"], rowInfo) !== undefined;
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

export default IndexVolumeGroupsTable;
