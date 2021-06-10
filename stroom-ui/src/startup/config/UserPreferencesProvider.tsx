import * as React from "react";

import UserPreferencesContext from "./UserPreferencesContext";
import CustomLoader from "../../components/CustomLoader";
import useUserPreferencesResource from "./useUserPreferencesResource";

const UserPreferencesProvider: React.FunctionComponent = ({ children }) => {
  const { userPreferences } = useUserPreferencesResource();

  if (!userPreferences) {
    return (
      <CustomLoader
        title="Stroom"
        message="Loading UserPreferences. Please wait..."
      />
    );
  } else {
    const body = document.getElementsByTagName("body")[0] as HTMLElement;
    body.className = userPreferences.theme;
  }

  return (
    <UserPreferencesContext.Provider value={userPreferences}>
      {children}
    </UserPreferencesContext.Provider>
  );
};

export default UserPreferencesProvider;
