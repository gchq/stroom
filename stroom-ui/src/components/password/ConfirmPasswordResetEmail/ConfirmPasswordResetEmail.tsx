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
import Button from "components/Button";

const ConfirmPasswordResetEmail: React.FunctionComponent<{
  onBack: Function;
}> = ({ onBack }) => (
  <div className="container">
    <h3>Password reset</h3>
    <p>Please check your email. </p>
    <p>
      <strong>If the email address is registered</strong> you should shortly
      receive a message with a link that will let you change your password.
    </p>
    <div className="footer">
      <Button onClick={() => onBack()} text="Back to Stroom" />
    </div>
  </div>
);

export default ConfirmPasswordResetEmail;
