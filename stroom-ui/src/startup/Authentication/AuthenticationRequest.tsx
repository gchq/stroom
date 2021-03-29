/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import useSessionApi from "./useSessionApi";
import useAuthenticationContext from "./useAuthenticationContext";

interface Props {
  referrer: string;
  loginUrl: string;
}

const AuthenticationRequest: React.FunctionComponent<Props> = ({
  referrer,
  loginUrl,
}) => {
  const { login } = useSessionApi();
  const { setIdToken } = useAuthenticationContext();

  React.useEffect(() => {
    login(referrer).then((response) => {
      if (response.authenticated) {
        setIdToken("Session authenticated");
      } else {
        window.location.href = response.redirectUri;
      }
    });

    // sendAuthenticationRequest(
    //     referrer,
    //     uiUrl,
    //     loginUrl,
    // );
  }, [login, setIdToken, referrer, loginUrl]);

  return null;
};

export default AuthenticationRequest;
