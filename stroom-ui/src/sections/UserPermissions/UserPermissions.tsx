import * as React from "react";

import { Formik, Field, Form } from "formik";
import {
  compose,
  lifecycle,
  withStateHandlers,
  StateHandlerMap
} from "recompose";
import { connect } from "react-redux";

import { findUsers, deleteUser } from "./client";
import { GlobalStoreState } from "../../startup/reducers";
import { User } from "../../types";
import IconHeader from "../../components/IconHeader";
import UsersTable from "./UsersTable";
import { RouteComponentProps, withRouter } from "react-router";
import Button from "../../components/Button";
import NewUserDialog from "./NewUserDialog";
import ThemedConfirm from "../../components/ThemedConfirm";

const LISTING_ID = "user_permissions";

export interface Props {}

interface ConnectState {
  users: Array<User>;
}

interface ConnectDispatch {
  findUsers: typeof findUsers;
  deleteUser: typeof deleteUser;
}

interface UserSelectionStateValues {
  selectedUser?: User;
  isNewDialogOpen: boolean;
  isDeleteDialogOpen: boolean;
}
interface UserSelectionStateHandlers {
  onSelection: (uuid?: string) => void;
  openNewDialog: () => void;
  closeNewDialog: () => void;
  openDeleteDialog: () => void;
  closeDeleteDialog: () => void;
}
interface UserSelectionState
  extends UserSelectionStateValues,
    UserSelectionStateHandlers {}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    UserSelectionState,
    RouteComponentProps<any> {}

const enhance = compose<EnhancedProps, Props>(
  withRouter,
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userPermissions: { users } }) => ({
      users: users[LISTING_ID]
    }),
    {
      findUsers,
      deleteUser
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
      selectedUser: undefined,
      isNewDialogOpen: false,
      isDeleteDialogOpen: false
    }),
    {
      onSelection: (_, { users }) => selectedUuid => ({
        selectedUser: users.find((u: User) => u.uuid === selectedUuid)
      }),
      openNewDialog: () => () => ({
        isNewDialogOpen: true
      }),
      closeNewDialog: () => () => ({
        isNewDialogOpen: false
      }),
      openDeleteDialog: () => () => ({
        isDeleteDialogOpen: true
      }),
      closeDeleteDialog: () => () => ({
        isDeleteDialogOpen: false
      })
    }
  )
);

interface Values {
  name: string;
  isGroup?: boolean;
  uuid: string;
}

const UserPermissions = ({
  users,
  findUsers,
  selectedUser,
  onSelection,
  history,
  isNewDialogOpen,
  openNewDialog,
  closeNewDialog,
  isDeleteDialogOpen,
  openDeleteDialog,
  closeDeleteDialog,
  deleteUser
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
      <Button text="Create" onClick={openNewDialog} />
      <Button
        text="View/Edit"
        disabled={!selectedUser}
        onClick={() => history.push(`/s/userPermissions/${selectedUser!.uuid}`)}
      />
      <Button
        text="Delete"
        disabled={!selectedUser}
        onClick={openDeleteDialog}
      />

      <ThemedConfirm
        question={
          selectedUser
            ? `Are you sure you want to delete user ${selectedUser.name}`
            : "no user?"
        }
        isOpen={isDeleteDialogOpen}
        onConfirm={() => {
          deleteUser(selectedUser!.uuid);
          closeDeleteDialog();
        }}
        onCancel={closeDeleteDialog}
      />
      <UsersTable {...{ users, onSelection, selectedUser }} />
    </div>

    <NewUserDialog isOpen={isNewDialogOpen} onCancel={closeNewDialog} />
  </div>
);

export default enhance(UserPermissions);
