import * as React from "react";

import { path } from "ramda";
import { Formik, Field, Form } from "formik";
import {
  compose,
  lifecycle,
  withStateHandlers,
  StateHandlerMap
} from "recompose";
import { connect } from "react-redux";
import ReactTable, { RowInfo } from "react-table";

import { findUsers } from "./userClient";
import withLocalStorage from "../../lib/withLocalStorage";
import { GlobalStoreState } from "../../startup/reducers";
import { User } from "../..//types";
import IconHeader from "../../components/IconHeader";
import HorizontalPanel from "../../components/HorizontalPanel";
import PanelGroup from "react-panelgroup";
import UsersInGroup from "./UsersInGroup";
import GroupsForUser from "./GroupsForUser";

const LISTING_ID = "user_permissions";

export interface Props {}

interface ConnectState {
  users: Array<User>;
}

interface ConnectDispatch {
  findUsers: typeof findUsers;
}
interface State {
  selectedUser?: User;
}
interface StateHandlers {
  onSelection: (uuid?: string) => void;
}
interface WithLocalStorage {
  tableHeight: number;
  editUserHeight: number;
  setTableHeight: (tableHeight: number) => void;
  setEditUserHeight: (editUserHeight: number) => void;
}
export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    State,
    StateHandlers,
    WithLocalStorage {}

const withListHeight = withLocalStorage("tableHeight", "setTableHeight", 500);
const withDetailsHeight = withLocalStorage(
  "editUserHeight",
  "setEditUserHeight",
  500
);

const enhance = compose<EnhancedProps, Props>(
  withListHeight,
  withDetailsHeight,
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userPermissions: { users } }) => ({
      users: users[LISTING_ID]
    }),
    {
      findUsers
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { findUsers } = this.props;

      findUsers(LISTING_ID);
    }
  }),
  withStateHandlers<State, StateHandlerMap<State>, ConnectState>(
    () => ({
      selectedUser: undefined
    }),
    {
      onSelection: (_, { users }) => selectedUuid => ({
        selectedUser: users.find((u: User) => u.uuid === selectedUuid)
      })
    }
  )
);

interface Values {
  name: string;
  isGroup?: boolean;
  uuid: string;
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

const EditUserPanel = ({ selectedUser, onSelection }: EnhancedProps) => (
  <HorizontalPanel
    title={
      selectedUser!.group
        ? `Users in Group ${selectedUser!.name}`
        : `Groups for User ${selectedUser!.name}`
    }
    onClose={() => onSelection()}
    content={
      selectedUser!.group ? (
        <UsersInGroup group={selectedUser!} />
      ) : (
        <GroupsForUser user={selectedUser!} />
      )
    }
  />
);

const UsersPermissionsTable = ({
  users,
  selectedUser,
  onSelection
}: EnhancedProps) => (
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

const UserPermissions = (props: EnhancedProps) => (
  <div className="UserPermissions">
    <IconHeader icon="users" text="User Permissions" />
    <Formik
      initialValues={{
        name: "",
        uuid: ""
      }}
      onSubmit={() => {}}
      validate={({ name, isGroup, uuid }: Values) =>
        props.findUsers(LISTING_ID, name, isGroup, uuid)
      }
    >
      <Form>
        <label htmlFor="name">Name</label>
        <Field name="name" type="text" />
        <label htmlFor="uuid">UUID</label>
        <Field name="uuid" type="text" />
        <label htmlFor="isGroup">Is Group</label>
        <Field name="isGroup" component="select" placeholder="Group">
          <option value="">N/A</option>
          <option value="true">Group</option>
          <option value="false">User</option>
        </Field>
      </Form>
    </Formik>
    <div className="UserTable__container">
      <div className="UserTable__reactTable__container">
        {!!props.selectedUser ? (
          <PanelGroup
            direction="row"
            panelWidths={[
              {
                resize: "dynamic",
                minSize: 100,
                size: props.tableHeight
              },
              {
                resize: "dynamic",
                minSize: 100,
                size: props.editUserHeight
              }
            ]}
            onUpdate={(panelWidths: any[]) => {
              props.setTableHeight(panelWidths[0].size);
              props.setEditUserHeight(panelWidths[1].size);
            }}
          >
            <UsersPermissionsTable {...props} />
            <EditUserPanel {...props} />
          </PanelGroup>
        ) : (
          <UsersPermissionsTable {...props} />
        )}
      </div>
    </div>
  </div>
);

export default enhance(UserPermissions);
