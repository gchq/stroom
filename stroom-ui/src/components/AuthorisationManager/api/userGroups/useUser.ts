import * as React from "react";

import useApi from "./useApi";
import { StroomUser } from ".";

const useUser = (userUuid: string): StroomUser | undefined => {
  const { fetchUser } = useApi();

  const [user, setUser] = React.useState<StroomUser | undefined>(undefined);
  React.useEffect(() => {
    fetchUser(userUuid).then(setUser);
  }, [userUuid, fetchUser, setUser]);

  return user;
};

export default useUser;
