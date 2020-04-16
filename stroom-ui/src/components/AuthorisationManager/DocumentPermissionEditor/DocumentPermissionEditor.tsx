import * as React from "react";
import useDocumentPermissions from "components/AuthorisationManager/api/docPermission/useDocumentPermissions";
import {
  StroomUser,
  useUsers,
} from "components/AuthorisationManager/api/userGroups";
import UserPickerDialog, {
  useDialog as userUserPickerDialog,
} from "components/AuthorisationManager/UserPickerDialog";
import Button from "components/Button";
import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import IconHeader from "components/IconHeader";
import ThemedConfirm, {
  useDialog as useThemedConfirm,
} from "components/ThemedConfirm";
import useRouter from "lib/useRouter";
import useAppNavigation from "lib/useAppNavigation";
import UsersTable, { useTable as useUsersTable } from "../UsersTable";

interface Props {
  docRefUuid: string;
}

export const DocumentPermissionEditor: React.FunctionComponent<Props> = ({
  docRefUuid,
}) => {
  const {
    nav: { goToAuthorisationsForDocumentForUser },
  } = useAppNavigation();
  const {
    clearPermissions,
    clearPermissionForUser,
    preparePermissionsForUser,
    permissionsByUser,
  } = useDocumentPermissions(docRefUuid);

  const { history } = useRouter();
  const userUuids = React.useMemo(() => Object.keys(permissionsByUser), [
    permissionsByUser,
  ]);
  const users = useUsers(userUuids);
  const { componentProps: usersTableProps } = useUsersTable(users);

  const { findDocRefWithLineage } = useDocumentTree();
  const { node: docRef } = React.useMemo(
    () => findDocRefWithLineage(docRefUuid),
    [findDocRefWithLineage, docRefUuid],
  );
  const {
    selectableTableProps: { selectedItems: selectedUsers, clearSelection },
  } = usersTableProps;
  const selectedUser: StroomUser | undefined =
    selectedUsers.length > 0 ? selectedUsers[0] : undefined;

  const {
    componentProps: userPickerProps,
    showDialog: showUserPicker,
  } = userUserPickerDialog({
    pickerBaseProps: { isGroup: undefined },
    onConfirm: preparePermissionsForUser,
  });

  const {
    showDialog: showConfirmClear,
    componentProps: confirmClearProps,
  } = useThemedConfirm({
    getQuestion: React.useCallback(
      () =>
        `Are you sure you wish to clear permissions for ${
          selectedUsers.length === 0 ? "all" : "selected"
        } users?`,
      [selectedUsers.length],
    ),
    getDetails: React.useCallback(() => {
      if (selectedUsers.length === 0) {
        return `From Document ${docRef.type} - ${docRefUuid}`;
      } else {
        return (
          `From Document ${docRef.type} - ${docRefUuid} for users ` +
          selectedUsers.map(u => u.name).join(", ")
        );
      }
    }, [docRefUuid, docRef, selectedUsers]),
    onConfirm: React.useCallback(() => {
      if (selectedUsers.length !== 0) {
        selectedUsers.forEach(user => clearPermissionForUser(user.uuid));
        clearSelection();
      } else {
        clearPermissions();
      }
    }, [
      selectedUsers,
      clearSelection,
      clearPermissionForUser,
      clearPermissions,
    ]),
  });
  const clearButtonText =
    selectedUsers.length === 0 ? "Clear All" : "Clear Selected";

  const onClickEdit = React.useCallback(() => {
    if (!!selectedUser) {
      goToAuthorisationsForDocumentForUser(docRefUuid, selectedUser.uuid);
    }
  }, [docRefUuid, selectedUser, goToAuthorisationsForDocumentForUser]);

  return (
    <div>
      <IconHeader
        icon="key"
        text={`Document Permissions for ${docRef.type} - ${docRef.name}`}
      />
      <div>
        <Button text="Back" onClick={history.goBack} />
        <Button text="Add" onClick={showUserPicker} />
        <Button
          text="View/Edit"
          disabled={selectedUsers.length !== 1}
          onClick={onClickEdit}
        />
        <Button text={clearButtonText} onClick={showConfirmClear} />

        <h2>Users</h2>
        <UsersTable {...usersTableProps} />

        <ThemedConfirm {...confirmClearProps} />
        <UserPickerDialog {...userPickerProps} />
      </div>
    </div>
  );
};

export default DocumentPermissionEditor;
