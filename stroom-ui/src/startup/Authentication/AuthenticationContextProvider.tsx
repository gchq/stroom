import * as React from "react";

import AuthenticationContext from "./AuthenticationContext";

const AuthenticationContextProvider: React.FunctionComponent = ({
  children,
}) => {
  const [idToken, setIdToken] = React.useState<string | undefined>(undefined);

  return (
    <AuthenticationContext.Provider value={{ idToken, setIdToken }}>
      {children}
    </AuthenticationContext.Provider>
  );
};

export default AuthenticationContextProvider;
