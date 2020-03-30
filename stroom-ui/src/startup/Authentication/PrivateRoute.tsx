/*
 * Copyright 2018 Crown Copyright
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
import {Route, RouteProps} from "react-router-dom";
import useConfig from "../config/useConfig";
import AuthenticationRequest from "./AuthenticationRequest";
import useAuthenticationContext from "./useAuthenticationContext";
import useServiceUrl from "../config/useServiceUrl";

const PrivateRoute = ({render, ...rest}: RouteProps) => {
    const {advertisedUrl} = useConfig();
    const {loginServiceUrl} = useServiceUrl();
    const {idToken} = useAuthenticationContext();

    if (
        !(
            advertisedUrl !== undefined &&
            loginServiceUrl !== undefined
        )
    ) {
        throw new Error(
            `Config Not Correct for Private Routes ${JSON.stringify({
                advertisedUrl,
                loginServiceUrl,
            })}`,
        );
    }

    return (
        <Route
            {...rest}
            render={props =>
                !!idToken ? (
                    render && render({...props})
                ) : (
                    <AuthenticationRequest
                        referrer={props.location.pathname}
                        uiUrl={advertisedUrl}
                        loginUrl={loginServiceUrl}
                    />
                )
            }
        />
    );
};

export default PrivateRoute;
