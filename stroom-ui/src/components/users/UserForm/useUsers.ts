import { useCallback } from "react";
import useAppNavigation from "lib/useAppNavigation";
import { useApi as useAuthorisationApi } from "components/authorisation";
import useApi from "../api/useApi";
import { Account } from "../types";
import useUserState from "./useUserState";

/**
 * This hook connects the REST API calls to the Redux Store.
 */
const useUsers = () => {
  const { account, setAccount, setIsCreating } = useUserState();

  const {
    nav: { goToUsers },
  } = useAppNavigation();

  /**
   * Updates the user
   */
  const {
    change: updateUserUsingApi,
    add: createAccountUsingApi,
    fetch: fetchUserUsingApi,
  } = useApi();
  const { createUser: createAuthorisationUser } = useAuthorisationApi();

  const updateUser = useCallback(
    (user: Account) => {
      updateUserUsingApi(user).then(() => {
        goToUsers();
      });
    },
    [updateUserUsingApi, goToUsers],
  );

  /**
   * Creates a user
   */
  const createUser = useCallback(
    (account: Account) => {
      createAccountUsingApi(account).then(() => {
        createAuthorisationUser(account.email).then(() => {
          setIsCreating(false);
          goToUsers();
        });
      });
    },
    [goToUsers, createAccountUsingApi, createAuthorisationUser, setIsCreating],
  );

  /**
   * Fetches a user by id/email, and puts it into the redux state.
   */
  const fetchUser = useCallback(
    (userId: string) => {
      fetchUserUsingApi(userId).then(account => {
        setIsCreating(false);
        setAccount(account);
      });
    },
    [fetchUserUsingApi, setIsCreating, setAccount],
  );

  return {
    updateUser,
    createUser,
    fetchUser,
    account: account,
  };
};

export default useUsers;
