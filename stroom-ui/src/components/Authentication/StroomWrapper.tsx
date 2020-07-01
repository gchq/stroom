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
import UsersManager from "../users/SearchUsers/UsersManager";

enum DialogType {
  CHANGE_PASSWORD,
  MANAGE_USERS,
}

const StroomWrapper: FunctionComponent<{
  userId: string;
}> = (props) => {
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
  return (
    <React.Fragment>
      <iframe
        className="StroomWrapper__iframe"
        title="stroom"
        src="http://localhost:8080/stroom/ui"
      />
      {dialogType === DialogType.CHANGE_PASSWORD ? (
        <ChangePasswordManager userId={props.userId} onClose={onClose} />
      ) : undefined}
      {dialogType === DialogType.MANAGE_USERS ? (
        <UsersManager onClose={onClose} />
      ) : undefined}
    </React.Fragment>
  );
};

export default StroomWrapper;
