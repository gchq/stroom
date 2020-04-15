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
import ChangePasswordFields from "../ChangePasswordFields";

const ResetPassword: React.FunctionComponent<{
  isTokenMissing: boolean;
  isTokenInvalid: boolean;
  isTokenExpired: boolean;
  onSubmit: Function;
  onValidate: (
    oldPassword: string,
    password: string,
    verifyPassword: string,
    email: string,
  ) => Promise<string>;
}> = ({
  isTokenExpired,
  isTokenInvalid,
  isTokenMissing,
  onSubmit,
  onValidate,
}) => {
  const failure = (
    <div>
      <h4>Unable to reset password!</h4>
      {isTokenMissing || isTokenInvalid ? (
        <p>I&apos;m afraid this password reset link is broken.</p>
      ) : (
        undefined
      )}
      {isTokenExpired ? (
        <p>I&apos;m afraid this password reset link has expired.</p>
      ) : (
        undefined
      )}
    </div>
  );

  const showFailure = isTokenMissing || isTokenInvalid || isTokenExpired;
  const showChangePasswordFields = !showFailure;
  return (
    <div className="container">
      <div className="section">
        <div className="section__title">
          <h3>Reset your password</h3>
        </div>
        {showFailure ? failure : undefined}
        {showChangePasswordFields ? (
          <ChangePasswordFields
            showOldPasswordField={false}
            onSubmit={onSubmit}
            onValidate={(password, oldPassword, verifyPassword, email) =>
              onValidate(oldPassword, password, verifyPassword, email)
            }
          />
        ) : (
          undefined
        )}
      </div>
    </div>
  );
};

export default ResetPassword;
