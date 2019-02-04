import * as React from "react";

import { Formik, Field, Form } from "formik";
import {
  compose,
  lifecycle,
  withStateHandlers,
  StateHandlerMap
} from "recompose";
import { connect } from "react-redux";

import { findUsers } from "./client";
import withLocalStorage from "../../lib/withLocalStorage";
import { GlobalStoreState } from "../../startup/reducers";
import { User } from "../../types";
import IconHeader from "../../components/IconHeader";
import HorizontalPanel from "../../components/HorizontalPanel";
import PanelGroup from "react-panelgroup";
import UsersInGroup from "./UsersInGroup";
import GroupsForUser from "./GroupsForUser";
import UsersTable from "./UsersTable";

const LISTING_ID = "user_permissions";

export interface Props {}

interface ConnectState {
  users: Array<User>;
}

interface ConnectDispatch {
  findUsers: typeof findUsers;
}
interface UserSelectionStateValues {
  selectedUser?: User;
}
interface UserSelectionStateHandlers {
  onSelection: (uuid?: string) => void;
}
interface UserSelectionState
  extends UserSelectionStateValues,
    UserSelectionStateHandlers {}

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
    UserSelectionState,
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
  withStateHandlers<
    UserSelectionStateValues,
    StateHandlerMap<UserSelectionStateValues>,
    ConnectState
  >(
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

const EditUserPanel = ({ selectedUser, onSelection }: UserSelectionState) => (
  <HorizontalPanel
    title={
      selectedUser!.isGroup
        ? `Users in Group ${selectedUser!.name}`
        : `Groups for User ${selectedUser!.name}`
    }
    onClose={() => onSelection()}
    content={
      selectedUser!.isGroup ? (
        <UsersInGroup group={selectedUser!} />
      ) : (
        <GroupsForUser user={selectedUser!} />
      )
    }
  />
);

const UserPermissions = ({
  users,
  findUsers,
  selectedUser,
  onSelection,
  tableHeight,
  editUserHeight,
  setTableHeight,
  setEditUserHeight
}: EnhancedProps) => (
  <div className="UserPermissions">
    <IconHeader icon="users" text="User Permissions" />
    <Formik
      initialValues={{
        name: "",
        uuid: ""
      }}
      onSubmit={() => {}}
      validate={({ name, isGroup, uuid }: Values) =>
        findUsers(LISTING_ID, name, isGroup, uuid)
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
        {!!selectedUser ? (
          <PanelGroup
            direction="row"
            panelWidths={[
              {
                resize: "dynamic",
                minSize: 100,
                size: tableHeight
              },
              {
                resize: "dynamic",
                minSize: 100,
                size: editUserHeight
              }
            ]}
            onUpdate={(panelWidths: any[]) => {
              setTableHeight(panelWidths[0].size);
              setEditUserHeight(panelWidths[1].size);
            }}
          >
            <UsersTable {...{ users, onSelection, selectedUser }} />
            <EditUserPanel {...{ onSelection, selectedUser }} />
          </PanelGroup>
        ) : (
          <UsersTable {...{ users, onSelection, selectedUser }} />
        )}
      </div>
    </div>
  </div>
);

export default enhance(UserPermissions);
