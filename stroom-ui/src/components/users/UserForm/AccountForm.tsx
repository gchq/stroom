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

import { Form, Formik } from "formik";
import { isEmpty } from "ramda";
import * as React from "react";
import { useState } from "react";
import Button from "components/Button";
import { hasAnyProps } from "lib/lang";
import BackConfirmation from "../BackConfirmation";
import { UserValidationSchema } from "../validation";
import EditUserFormProps from "./EditUserFormProps";
import UserFields from "./UserFields";

const AccountForm: React.FunctionComponent<EditUserFormProps> = ({
  onSubmit,
  onBack,
  onCancel,
  onValidate,
  account,
}) => {
  const [showBackConfirmation, setShowBackConfirmation] = useState(false);
  const handleBack = (isPristine: boolean) => {
    if (isPristine) {
      onBack();
    } else {
      setShowBackConfirmation(true);
    }
  };
  account.password = "";
  return (
    <Formik
      onSubmit={values => onSubmit(values)}
      initialValues={{
        ...account,
        verifyPassword: account.password,
        neverExpires: account.neverExpires || false,
      }}
      validateOnBlur
      // validate={onValidate}
      validationSchema={UserValidationSchema}
    >
      {({ errors, touched, submitForm, setFieldTouched, setFieldValue }) => {
        const isPristine = !hasAnyProps(touched);
        const hasErrors = hasAnyProps(errors);
        return (
          <Form>
            <div className="header">
              <Button
                onClick={() => handleBack(isPristine)}
                icon="arrow-left"
                text="Back"
              />
            </div>
            <div>
              <UserFields
                showCalculatedFields
                userBeingEdited={account}
                errors={errors}
                touched={touched}
                setFieldTouched={setFieldTouched}
                setFieldValue={setFieldValue}
              />
              <div className="footer">
                <Button
                  appearance="contained"
                  action="primary"
                  type="submit"
                  disabled={isPristine || hasErrors}
                  icon="save"
                  text="Save"
                  // isLoading={isSaving}
                />
                <Button
                  appearance="contained"
                  action="secondary"
                  icon="times"
                  onClick={() => onCancel()}
                  text="Cancel"
                />
              </div>
              <BackConfirmation
                isOpen={showBackConfirmation}
                onGoBack={() => onBack()}
                hasErrors={!isEmpty(errors)}
                onSaveAndGoBack={submitForm}
                onContinueEditing={() => setShowBackConfirmation(false)}
              />
            </div>
          </Form>
        );
      }}
    </Formik>
  );
};

export default AccountForm;
