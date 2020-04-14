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

import { AuditCopy, LoginStatsCopy } from "components/auditCopy";
import { ErrorMessage, Field, FormikErrors, FormikTouched } from "formik";
import * as React from "react";
import Toggle from "react-toggle";
import "react-toggle/style.css";
import { User } from "../types";
import useConfig from "startup/config/useConfig";

const LoginFailureCopy = ({ attemptCount }: { attemptCount: number }) => (
  <div className="copy">
    Login attempts with an incorrect password: {attemptCount}
  </div>
);

const CheckboxField = ({ field, ...props }: { field: any }) => {
  return (
    <Toggle
      icons={false}
      checked={field.value}
      onChange={field.onChange}
      onBlur={field.onBlur}
      name={field.name}
      {...props}
    />
  );
};

const UserFields = ({
  showCalculatedFields,
  userBeingEdited,
  setFieldTouched,
  setFieldValue,
}: {
  showCalculatedFields: boolean;
  errors: FormikErrors<User>;
  touched: FormikTouched<User>;
  userBeingEdited?: User;
  setFieldTouched: Function;
  setFieldValue: Function;
}) => {
  const { dateFormat } = useConfig();
  return (
    <div className="container">
      <Field name="id" type="hidden" />
      <div className="section">
        <div className="section__title">
          <h3>Account</h3>
        </div>
        <div className="section__fields">
          <div className="section__fields__row">
            <div className="field-container vertical">
              <label>First name</label>
              <Field name="firstName" type="text" label="First name" />
            </div>
            <div className="field-container__spacer" />
            <div className="field-container vertical">
              <label>Last name</label>
              <Field name="lastName" type="text" label="Last name" />
            </div>
          </div>
          <div className="section__fields">
            <div className="section__fields__row">
              <div className="field-container vertical">
                <label>Email</label>
                <div className="field-container--with-validation">
                  <Field name="email" label="Email" />
                  <ErrorMessage
                    name="email"
                    render={msg => (
                      <div className="validation-error">{msg}</div>
                    )}
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="section">
          <div className="section__title">
            <h3>Status</h3>
          </div>
          <div className="section__fields">
            <div className="section__fields__row">
              <div className="field-container vertical">
                <label>Account status</label>
                <Field
                  name="state"
                  component="select"
                  onChange={(event: any) => {
                    setFieldValue("state", event.target.value);
                    setFieldTouched("state");
                  }}
                >
                  <option value="enabled">Active</option>
                  <option value="disabled">Disabled</option>
                  <option disabled value="inactive">
                    Inactive (because of disuse)
                  </option>
                  <option disabled value="locked">
                    Locked (because of failed logins)
                  </option>
                </Field>
              </div>
              <div className="field-container__spacer" />
              <div className="field-container--with-validation">
                <label>Never expires?</label>
                <Field
                  name="neverExpires"
                  label="neverExpires"
                  component={CheckboxField}
                />
                <ErrorMessage
                  name="neverExpires"
                  render={msg => <div className="validation-error">{msg}</div>}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="section">
          <div className="section__title">
            <h3>Password</h3>
          </div>
          <div className="section__fields">
            <div className="section__fields__row">
              <div className="field-container vertical">
                <label>Password</label>
                <div className="field-container--with-validation">
                  <Field name="password" type="password" label="Password" />
                  <ErrorMessage
                    name="password"
                    render={msg => (
                      <div className="validation-error">{msg}</div>
                    )}
                  />
                </div>
              </div>
              <div className="field-container__spacer" />
              <div className="field-container vertical">
                <label>Verify password</label>
                <div className="field-container--with-validation">
                  <Field name="verifyPassword" type="password" />
                  <ErrorMessage
                    name="verifyPassword"
                    render={msg => (
                      <div className="validation-error">{msg}</div>
                    )}
                  />
                </div>
              </div>
            </div>
          </div>
          <div className="section__fields__row">
            <div className="field-container">
              <label>Force a password change at next login</label>
              <div className="field-container__spacer" />
              <div className="field-container--with-validation">
                <Field
                  name="forcePasswordChange"
                  label="forcePasswordChange"
                  component={CheckboxField}
                />
              </div>
            </div>
          </div>
        </div>

        <div className="section">
          <div className="section__title">
            <h3>Comments</h3>
          </div>
          <div className="section__fields">
            <div className="section__fields__row 1-column">
              <Field
                className="section__fields__comments"
                name="comments"
                component="textarea"
              />
            </div>
          </div>
        </div>

        {showCalculatedFields && !!userBeingEdited ? (
          <React.Fragment>
            {!!userBeingEdited.loginCount ? (
              <div className="section">
                <div className="section__title">
                  <h3>Audit</h3>
                </div>
                <div className="section__fields--copy-only">
                  <div className="section__fields_row">
                    <LoginFailureCopy
                      attemptCount={userBeingEdited.loginCount}
                    />
                    <LoginStatsCopy
                      lastLogin={userBeingEdited.lastLogin}
                      loginCount={userBeingEdited.loginCount}
                      dateFormat={dateFormat}
                    />
                  </div>
                </div>
              </div>
            ) : (
              undefined
            )}

            <div className="section">
              <div className="section__title">
                <h3>Audit</h3>
              </div>
              <div className="section__fields--copy-only">
                <div className="section__fields__rows">
                  <AuditCopy
                    createdOn={userBeingEdited.createdOn}
                    createdBy={userBeingEdited.createdByUser}
                    updatedOn={userBeingEdited.updatedOn}
                    updatedBy={userBeingEdited.updatedByUser}
                    dateFormat={dateFormat}
                  />
                </div>
              </div>
            </div>
          </React.Fragment>
        ) : (
          undefined
        )}
      </div>
    </div>
  );
};

export default UserFields;
