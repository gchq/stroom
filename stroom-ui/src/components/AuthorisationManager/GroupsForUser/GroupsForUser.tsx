import * as React from "react";
import { useAppNavigation } from "lib/useAppNavigation";
import {
  StroomUser,
  useGroupsForUser,
} from "components/AuthorisationManager/api/userGroups";
import Button from "components/Button";
import ThemedConfirm, {
  useDialog as useThemedConfirm,
} from "components/ThemedConfirm";
import {
  useDialog as useUserPickerDialog,
  UserPickerDialog,
} from "../UserPickerDialog";
import UsersTable, { useTable as useUsersTable } from "../UsersTable";

interface Props {
  user: StroomUser;
}

const GroupsForUser: React.FunctionComponent<Props> = ({ user }) => {
  const { groups, addToGroup, removeFromGroup } = useGroupsForUser(user);
  const {
    nav: { goToAuthorisationsForUser },
  } = useAppNavigation();

  const { componentProps: tableProps } = useUsersTable(groups);
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
          .map((g) => g.uuid)
          .forEach((gUuid) => removeFromGroup(gUuid)),
      [removeFromGroup, selectedItems],
    ),
    getQuestion: React.useCallback(
      () => "Are you sure you want to remove the user from these groups?",
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
    componentProps: userGroupPickerProps,
    showDialog: showUserGroupPicker,
  } = useUserPickerDialog({
    onConfirm: React.useCallback((groupUuid: string) => addToGroup(groupUuid), [
      addToGroup,
    ]),
    pickerBaseProps: {
      valuesToFilterOut: React.useMemo(() => groups.map((g) => g.uuid), [
        groups,
      ]),
    },
  });

  return (
    <div>
      <h2>Groups for User {user.name}</h2>
      <Button text="Add to Group" onClick={showUserGroupPicker} />
      <Button
        text="Remove from Group"
        disabled={selectedItems.length === 0}
        onClick={showDeleteGroupMembershipDialog}
      />
      <Button
        text="View/Edit"
        disabled={selectedItems.length !== 1}
        onClick={goToSelectedUser}
      />
      <ThemedConfirm {...deleteGroupMembershipComponentProps} />
      <UsersTable {...tableProps} />
      <UserPickerDialog {...userGroupPickerProps} />
    </div>
  );
};

export default GroupsForUser;
