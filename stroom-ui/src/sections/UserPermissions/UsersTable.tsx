import * as React from "react";

import { path } from "ramda";
import ReactTable, { RowInfo } from "react-table";

import { User } from "../../types";

export interface Props {
  users: Array<User>;
  selectedUser?: User;
  onSelection: (uuid?: string) => void;
}

const USER_COLUMNS = [
  {
    id: "uuid",
    Header: "UUID",
    accessor: (u: User) => u.uuid
  },
  {
    id: "name",
    Header: "Name",
    accessor: (u: User) => u.name
  },
  {
    id: "isGroup",
    Header: "Is Group",
    accessor: (u: User) => (u.group ? "Group" : "User")
  }
];

const UsersTable = ({ users, selectedUser, onSelection }: Props) => (
  <ReactTable
    data={users}
    columns={USER_COLUMNS}
    getTdProps={(state: any, rowInfo: RowInfo) => {
      return {
        onClick: (_: any, handleOriginal: () => void) => {
          if (rowInfo !== undefined) {
            if (!!selectedUser && selectedUser.uuid === rowInfo.original.uuid) {
              onSelection();
            } else {
              onSelection(rowInfo.original.uuid);
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
        !!selectedUser &&
        path(["original", "uuid"], rowInfo) === selectedUser.uuid;
      const hasData = path(["original", "uuid"], rowInfo) !== undefined;
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

export default UsersTable;
