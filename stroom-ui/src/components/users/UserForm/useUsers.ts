import { useCallback } from "react";
import useAppNavigation from "lib/useAppNavigation";
import { useApi as useAuthorisationApi } from "components/authorisation";
import useApi from "../api/useApi";
import { User } from "../types";
import useUserState from "./useUserState";

/**
 * This hook connects the REST API calls to the Redux Store.
 */
const useUsers = () => {
  const { user, setUser, setIsCreating } = useUserState();

  const {
    nav: { goToUsers },
  } = useAppNavigation();

  /**
   * Updates the user
   */
  const { change: updateUserUsingApi } = useApi();
  const updateUser = useCallback(
    (user: User) => {
      updateUserUsingApi(user).then(() => {
        goToUsers();
      });
    },
    [updateUserUsingApi, goToUsers],
  );

  /**
   * Creates a user
   */
  const { createUser: createAuthorisationUser } = useAuthorisationApi();
  const { add: createUserUsingApi } = useApi();
  const createUser = useCallback(
    (user: User) => {
      createUserUsingApi(user).then(() => {
        createAuthorisationUser(user.email).then(() => {
          setIsCreating(false);
          goToUsers();
        });
      });
    },
    [goToUsers, createUserUsingApi, createAuthorisationUser, setIsCreating],
  );

  /**
   * Fetches a user by id/email, and puts it into the redux state.
   */
  const { fetch: fetchUserUsingApi } = useApi();
  const fetchUser = useCallback(
    (userId: string) => {
      fetchUserUsingApi(userId).then(user => {
        setIsCreating(false);
        setUser(user);
      });
    },
    [fetchUserUsingApi, setIsCreating, setUser],
  );

  return {
    updateUser,
    createUser,
    fetchUser,
    user,
  };
};

export default useUsers;
