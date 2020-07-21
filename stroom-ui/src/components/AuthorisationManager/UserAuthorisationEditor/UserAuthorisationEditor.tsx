import * as React from "react";

import IconHeader from "components/IconHeader";
import Button from "components/Button";
import UsersInGroup from "../UsersInGroup";
import GroupsForUser from "../GroupsForUser";
import { useUser } from "components/AuthorisationManager/api/userGroups";
import Loader from "components/Loader";
import useRouter from "lib/useRouter";
import { useAppPermissionsForUser } from "components/AuthorisationManager/api/appPermission";
import { AppPermissionPicker } from "../AppPermissionPicker";

interface Props {
  userUuid: string;
}

const UserAuthorisationEditor: React.FunctionComponent<Props> = ({
  userUuid,
}) => {
  const { history } = useRouter();
  const user = useUser(userUuid);
  const {
    userAppPermissions,
    addPermission,
    removePermission,
  } = useAppPermissionsForUser(userUuid);

  if (!user) {
    return <Loader message="Loading user..." />;
  }

  const title = user.group ? `Group ${user.name}` : `User ${user.name}`;

  return (
    <div>
      <IconHeader text={title} icon="user" />
      <Button onClick={history.goBack}>Back</Button>

      <section>
        <h2>Application Permissions</h2>
        <AppPermissionPicker
          {...{ value: userAppPermissions, addPermission, removePermission }}
        />
      </section>

      {user.group ? (
        <UsersInGroup group={user} />
      ) : (
        <GroupsForUser user={user} />
      )}
    </div>
  );
};

export default UserAuthorisationEditor;
