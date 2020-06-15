import * as React from "react";
import useAppNavigation from "lib/useAppNavigation";
import Button from "components/Button";
import ThemedConfirm, {
  useDialog as useThemedConfirm,
} from "components/ThemedConfirm";
import { StroomUser, useUsersInGroup } from "../api/userGroups";
import UserPickerDialog, {
  useDialog as useUserPickerDialog,
} from "../UserPickerDialog";
import UsersTable, { useTable as useUsersTable } from "../UsersTable";

interface Props {
  group: StroomUser;
}

const UsersInGroup = ({ group }: Props) => {
  const { users, addToGroup, removeFromGroup } = useUsersInGroup(group);
  const {
    nav: { goToAuthorisationsForUser },
  } = useAppNavigation();

  const { componentProps: tableProps } = useUsersTable(users);
  const {
    selectableTableProps: { selectedItems },
  } = tableProps;

  const {
    componentProps: deleteGroupMembershipComponentProps,
    showDialog: showDeleteGroupMembershipDialog,
  } = useThemedConfirm({
    onConfirm: React.useCallback(
      () =>
        selectedItems
          .map((s) => s.uuid)
          .forEach((uUuid) => removeFromGroup(uUuid)),
      [removeFromGroup, selectedItems],
    ),
    getQuestion: React.useCallback(
      () => "Are you sure you want to remove these users from the group?",
      [],
    ),
    getDetails: React.useCallback(
      () => selectedItems.map((s) => s.name).join(", "),
      [selectedItems],
    ),
  });

  const goToSelectedUser = React.useCallback(() => {
    if (selectedItems.length === 1) {
      goToAuthorisationsForUser(selectedItems[0].uuid);
    }
  }, [goToAuthorisationsForUser, selectedItems]);

  const {
    componentProps: userPickerProps,
    showDialog: showUserPicker,
  } = useUserPickerDialog({
    pickerBaseProps: { isGroup: false },
    onConfirm: addToGroup,
  });

  return (
    <div>
      <h2>Users in Group {group.name}</h2>
      <Button text="Add" onClick={showUserPicker} />
      <Button
        text="Remove Users"
        disabled={selectedItems.length === 0}
        onClick={showDeleteGroupMembershipDialog}
      />
      <Button
        text="View/Edit"
        disabled={selectedItems.length !== 1}
        onClick={goToSelectedUser}
      />
      <UsersTable {...tableProps} />
      <UserPickerDialog {...userPickerProps} />
      <ThemedConfirm {...deleteGroupMembershipComponentProps} />
    </div>
  );
};

export default UsersInGroup;
