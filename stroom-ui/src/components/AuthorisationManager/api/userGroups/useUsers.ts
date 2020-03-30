import useListReducer from "lib/useListReducer";
import * as React from "react";
import { StroomUser } from ".";
import useApi from "./useApi";

/**
 * Use this to convert a list of users UUID's into a list of user objects.
 *
 * @param userUuids The list of user UUID's to retrieve
 */
const useUsers = (userUuids: string[]): StroomUser[] => {
  const { fetchUser } = useApi();
  const { items: allUsers, addItem } = useListReducer<StroomUser>(
    u => u.uuid,
  );

  const users = React.useMemo(
    () => allUsers.filter(u => userUuids.includes(u.uuid)),
    [allUsers, userUuids],
  );

  // Don't feed 'users' into the [], otherwise it will re-run this as users come in
  // THIS NEEDS RETHINKING
  React.useEffect(() => {
    let userUuidsFound = allUsers.map(u => u.uuid);
    userUuids
      .filter(userUuid => !userUuidsFound.includes(userUuid))
      .forEach(userUuid => fetchUser(userUuid).then(user => addItem(user)));
  }, [allUsers, userUuids, fetchUser, addItem]);

  return users;
};

export default useUsers;
