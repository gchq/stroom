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

export interface FormValues {
  userId: string;
  password: string;
}

const StroomWrapper: FunctionComponent = () => {
  const [message, setMessage] = useState<string>(undefined);
  useEffect(() => {
    const handler = (event) => {
      const data = JSON.parse(event.data);
      console.log("Hello World?", data);
      setMessage(data.message);
    };

    window.addEventListener("message", handler);

    // clean up
    return () => window.removeEventListener("message", handler);
  }, []); // empty array => run only once

  let content = null;
  if (message && message === "changePassword") {
    content = <ChangePasswordManager />;
  }

  return (
    <React.Fragment>
      <iframe
        className="StroomWrapper__iframe"
        title="stroom"
        src="http://localhost:8080/stroom/ui"
      />
      {content}
    </React.Fragment>
  );
};

export default StroomWrapper;
