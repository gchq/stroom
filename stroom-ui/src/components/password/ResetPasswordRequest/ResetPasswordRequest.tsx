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

import { ErrorMessage, Field, Form, Formik, FormikHelpers } from "formik";
import * as React from "react";
import Button from "components/Button";
import { hasAnyProps } from "lib/lang";
import * as Yup from "yup";

const ValidationSchema = Yup.object().shape({
  email: Yup.string().required("Required"),
});

const ResetPasswordRequest: React.FunctionComponent<{
  onBack: () => void;
  onSubmit: (formData: any, formikHelpers: FormikHelpers<any>) => void;
}> = ({ onBack, onSubmit }) => (
  <Formik
    enableReinitialize={true}
    initialValues={{
      email: "",
    }}
    onSubmit={onSubmit}
    validationSchema={ValidationSchema}
  >
    {({ errors, touched }) => {
      const isPristine = !hasAnyProps(touched);
      const hasErrors = hasAnyProps(errors);
      return (
        <Form>
          <div className="container">
            <div className="section">
              <div className="section__title">
                <h3>Request a password reset</h3>
              </div>
              <div className="section__fields">
                <div className="section__fields_row">
                  <div className="field-container vertical">
                    <label>Your registered email address</label>
                    <Field name="email" type="text" />
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
            <div className="footer">
              <Button
                appearance="contained"
                action="primary"
                type="submit"
                disabled={isPristine || hasErrors}
                text="Send"
              />
              <Button
                appearance="contained"
                action="secondary"
                onClick={() => onBack()}
                text="Back to Stroom"
              />
            </div>
          </div>
        </Form>
      );
    }}
  </Formik>
);

export default ResetPasswordRequest;
