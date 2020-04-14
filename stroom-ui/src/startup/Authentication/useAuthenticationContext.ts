import * as React from "react";

import AuthenticationContext from "./AuthenticationContext";

const useAuthenticationContext = () => React.useContext(AuthenticationContext);

export default useAuthenticationContext;
