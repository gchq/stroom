import * as React from "react";
import { useAuthenticationContext } from "startup/Authentication";
import { AppPermissions } from "./types";
import AuthorisationContext from "./AuthorisationContext";
import useServiceUrl from "startup/config/useServiceUrl";

const AuthorisationContextProvider: React.FunctionComponent = ({
  children,
}) => {
  const [appPermissions, setAppPermissions] = React.useState<AppPermissions>(
    {},
  );
  const { idToken } = useAuthenticationContext();

  const { authorisationServiceUrl } = useServiceUrl();

  const setHasAppPermission = React.useCallback(
    (permissionName: string, hasPermission: boolean) => {
      setAppPermissions({
        ...appPermissions,
        [permissionName]: hasPermission,
      });
    },
    [appPermissions, setAppPermissions],
  );

  const fetchAppPermission = React.useCallback(
    (permissionName: string) => {
      return fetch(`${authorisationServiceUrl}/hasAppPermission`, {
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          Authorization: `Bearer ${idToken}`,
        },
        method: "post",
        mode: "cors",
        body: JSON.stringify({
          permissionName,
        }),
      }).then(response => {
        if (response.status === 401) {
          setHasAppPermission(permissionName, false);
        } else if (response.status === 200) {
          setHasAppPermission(permissionName, true);
        } else {
          console.log(
            `Unknown response from the authorisation service! ${response}`,
          );
        }
      });
    },
    [idToken, authorisationServiceUrl, setHasAppPermission],
  );

  return (
    <AuthorisationContext.Provider
      value={{ appPermissions, fetchAppPermission }}
    >
      {children}
    </AuthorisationContext.Provider>
  );
};

export default AuthorisationContextProvider;
