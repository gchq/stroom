/*
 * Copyright 2020 Crown Copyright
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
import { FunctionComponent, useEffect, useState } from "react";
import ChangePasswordManager from "./ChangePasswordManager";
import AccountManager from "../Account/AccountManager/AccountManager";
import BackgroundLogo from "../Layout/BackgroundLogo";
import FormContainer from "../Layout/FormContainer";
import useStroomSessionResource from "./api/useStroomSessionResource";
import CustomLoader from "../CustomLoader";
import Background from "../Layout/Background";

enum DialogType {
  CHANGE_PASSWORD,
  MANAGE_USERS,
}

const stroomUrl = process.env.REACT_APP_STROOM_URL;

const StroomWrapper: FunctionComponent = () => {
  // Client state
  const [userId, setUserId] = useState<string>();
  const { validateSession } = useStroomSessionResource();
  useEffect(() => {
    if (!userId) {
      validateSession(window.location.href).then((response) => {
        if (response) {
          if (response.valid) {
            setUserId(response.userId);
          } else {
            // If we fail to get the current user and permissions then we might
            // not have an authenticated session. Under normal circumstances the
            // server would have already sent a redirect response to initiate an
            // auth flow but in development when we are serving the UI outside of
            // Dropwizard we will not receive a redirect as the UI server has no
            // idea that we aren't authenticated. For this reason we must perform
            // manual redirection in development.
            window.location.href = response.redirectUri;

            // const redirectUri = window.location.href;
            // let url = redirectUri.split("/")[0];
            // url += "/s/signIn?redirect_uri=";
            // url += encodeURI(redirectUri);
            // window.location.href = url;
          }
        }
      });
    }
  }, [validateSession, userId, setUserId]);

  const [dialogType, setDialogType] = useState<DialogType>(undefined);
  useEffect(() => {
    const handler = (event) => {
      try {
        const data = JSON.parse(event.data);
        console.log("Message from Stroom:", data);

        if (data.message) {
          if (data.message === "changePassword") {
            setDialogType(DialogType.CHANGE_PASSWORD);
          } else if (data.message === "manageUsers") {
            setDialogType(DialogType.MANAGE_USERS);
          }
        }
      } catch (err) {
        // Ignore.
      }
    };

    window.addEventListener("message", handler);

    // clean up
    return () => window.removeEventListener("message", handler);
  }, []); // empty array => run only once

  const onClose = () => {
    setDialogType(undefined);
  };

  console.log("Render: StroomWrapper");

  // If we have no user and permissions yet then show loading.
  if (!userId) {
    return (
      <Background>
        <BackgroundLogo>
          <CustomLoader
            title="Stroom"
            message="Loading Application. Please wait..."
          />
        </BackgroundLogo>
      </Background>
    );
  } else {
    return (
      <React.Fragment>
        <iframe
          className="StroomWrapper__iframe"
          title="stroom"
          src={stroomUrl + window.location.search}
        />
        {dialogType === DialogType.CHANGE_PASSWORD ? (
          <ChangePasswordManager userId={userId} onClose={onClose} />
        ) : undefined}
        {dialogType === DialogType.MANAGE_USERS ? (
          <AccountManager onClose={onClose} />
        ) : undefined}
      </React.Fragment>
    );
  }
};

export default StroomWrapper;
