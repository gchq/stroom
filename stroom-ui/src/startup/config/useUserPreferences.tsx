import * as React from "react";
import UserPreferencesContext from "./UserPreferencesContext";
import { UserPreferences } from "../../api/stroom";

const useUserPreferences = (): UserPreferences =>
  React.useContext(UserPreferencesContext);

export default useUserPreferences;
