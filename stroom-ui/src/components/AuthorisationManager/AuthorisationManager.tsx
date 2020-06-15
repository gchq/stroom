import * as React from "react";
import { useManageUsers } from "components/AuthorisationManager/api/userGroups";
import Button from "components/Button";
import ThemedConfirm, {
  useDialog as useThemedConfim,
} from "components/ThemedConfirm";
import useAppNavigation from "lib/useAppNavigation";
import NewUserDialog, {
  useDialog as useNewUserDialog,
} from "./NewUserDialog/NewUserDialog";
import {
  useDialog as useUserPickerDialog,
  UserPickerDialog,
} from "./UserPickerDialog";
import UsersTable, { useTable } from "./UsersTable";
import Toggle from "react-toggle";
import IconHeader from "components/IconHeader";

interface Props {
  isGroup: boolean;
}

const Authorisation: React.FunctionComponent<Props> = ({ isGroup }) => {
  const [filterable, setFilteringEnabled] = React.useState(false);
  const {
    nav: { goToAuthorisationsForUser },
  } = useAppNavigation();
  const {
    findUsers,
    users,
    createUser,
    deleteUser,
    addUserToGroup,
  } = useManageUsers();

  const { componentProps: tableProps } = useTable(users, {
    filterable,
  });
  const {
    selectableTableProps: { selectedItems: selectedUsers },
  } = tableProps;

  const {
    componentProps: newDialogComponentProps,
    showDialog: showNewDialog,
  } = useNewUserDialog({ isGroup, onCreateUser: createUser });
  const {
    componentProps: deleteDialogProps,
    showDialog: showDeleteDialog,
  } = useThemedConfim({
    getQuestion: React.useCallback(
      () => `Are you sure you want to delete user`,
      [],
    ),
    getDetails: React.useCallback(
      () => selectedUsers.map((v) => v.name).join(", "),
      [selectedUsers],
    ),
    onConfirm: React.useCallback(() => {
      selectedUsers.forEach((v) => deleteUser(v.uuid));
    }, [selectedUsers, deleteUser]),
  });

  const {
    componentProps: userGroupPickerProps,
    showDialog: showGroupPicker,
  } = useUserPickerDialog({
    onConfirm: React.useCallback(
      (groupUuid: string) =>
        selectedUsers.forEach((u) => {
          addUserToGroup(u.uuid, groupUuid);
        }),
      [addUserToGroup, selectedUsers],
    ),
  });

  const onViewEditClick = React.useCallback(() => {
    if (selectedUsers.length === 1) {
      goToAuthorisationsForUser(selectedUsers[0].uuid);
    }
  }, [selectedUsers, goToAuthorisationsForUser]);

  // Ensure find users is called
  React.useEffect(() => findUsers(undefined, isGroup), [isGroup, findUsers]);

  return (
    <div className="page">
      <div className="page__header">
        <IconHeader text={isGroup ? "User Groups" : "Users"} icon="user" />
        <div className="page__buttons Button__container">
          <Button onClick={showNewDialog} icon="plus" text="Create" />
          <Button
            disabled={selectedUsers.length !== 1}
            onClick={onViewEditClick}
            icon="edit"
            text="View/edit"
          />
          <Button
            disabled={selectedUsers.length !== 1}
            onClick={showDeleteDialog}
            icon="trash"
            text="Delete"
          />
          <Button
            text="To Group"
            icon="plus"
            disabled={
              selectedUsers.length === 0 || !!selectedUsers.find((u) => u.group)
            }
            onClick={showGroupPicker}
          />
          <div className="UserSearch-filteringToggle">
            <label>Show filtering</label>
            <Toggle
              icons={false}
              checked={filterable}
              onChange={(event) => setFilteringEnabled(event.target.checked)}
            />
          </div>
        </div>
      </div>
      <div className="page__body">
        <UsersTable {...tableProps} />
      </div>

      <UserPickerDialog {...userGroupPickerProps} />
      <ThemedConfirm {...deleteDialogProps} />
      <NewUserDialog {...newDialogComponentProps} />
    </div>
  );
};

export default Authorisation;
