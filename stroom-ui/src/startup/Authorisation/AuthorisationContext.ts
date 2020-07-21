import * as React from "react";
import { AppPermissions } from "./types";

interface AuthorisationContextApi {
  appPermissions: AppPermissions;
  fetchAppPermission: (appPermission: string) => void;
}

const AuthorisationContext: React.Context<AuthorisationContextApi> = React.createContext(
  {
    appPermissions: {},
    fetchAppPermission: (appPermission: string) => {
      console.error(
        "Default Implementation for Authorisation Context",
        appPermission,
      );
    },
  },
);

export default AuthorisationContext;
