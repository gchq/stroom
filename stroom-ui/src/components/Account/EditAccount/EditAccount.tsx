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
import {
  TextBoxFormField,
  TextAreaFormField,
  CheckBoxFormField,
} from "components/FormField";
import { Dialog } from "components/Dialog/Dialog";
import { PersonFill } from "react-bootstrap-icons";
import { OkCancelButtons, OkCancelProps } from "../../Dialog/OkCancelButtons";
import { Formik, FormikProps } from "formik";
import { Col, Form, Modal } from "react-bootstrap";
import { newAccountValidationSchema } from "./validation";
import { CreateAccountRequest, UpdateAccountRequest } from "../api/types";
import { Account } from "components/Account/types";
import Button from "../../Button/Button";
import { useState } from "react";
import {
  ChangePasswordDialog,
  ChangePasswordFormValues,
} from "../../Authentication/ChangePassword";
import { FormikHelpers } from "formik/dist/types";
import { PasswordPolicyConfig } from "../../Authentication/api/types";
import { useAccountResource } from "../api";

interface ChangePasswordProps {
  onPasswordChange: () => void;
}

export interface EditAccountProps extends ChangePasswordProps {
  initialValues: Account;
  onSubmit: (values: Account, actions: FormikHelpers<Account>) => void;
  onClose: (success: boolean) => void;
}

export interface EditAccountFormProps {
  formikProps: FormikProps<Account>;
  okCancelProps: OkCancelProps;
  changePasswordProps: ChangePasswordProps;
}

const EditAccountForm: React.FunctionComponent<EditAccountFormProps> = ({
  formikProps,
  okCancelProps,
  changePasswordProps,
}) => {
  const { values, handleSubmit, isSubmitting } = formikProps;
  const { onCancel, cancelClicked } = okCancelProps;
  const { onPasswordChange } = changePasswordProps;

  return (
    <Form noValidate={true} onSubmit={handleSubmit} className="EditAccount">
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">
          <PersonFill className="mr-3" />
          {values.id ? "Edit Account" : "Create Account"}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form.Row>
          <TextBoxFormField
            controlId="userId"
            type="text"
            label="User Id"
            placeholder="Enter A User Id"
            autoFocus={true}
            autoComplete="user-id"
            formikProps={formikProps}
          />
          <TextBoxFormField
            controlId="email"
            type="text"
            label="Email"
            placeholder="Enter An Email Address"
            autoComplete="email"
            formikProps={formikProps}
          />
        </Form.Row>
        <Form.Row>
          <TextBoxFormField
            controlId="firstName"
            type="text"
            label="First Name"
            placeholder="Enter First Name"
            autoComplete="first-name"
            formikProps={formikProps}
          />
          <TextBoxFormField
            controlId="lastName"
            type="text"
            label="Last Name"
            placeholder="Enter Last Name"
            autoComplete="last-name"
            formikProps={formikProps}
          />
        </Form.Row>
        <Form.Row>
          <TextAreaFormField
            controlId="comments"
            label="Comments"
            placeholder="Add Comments"
            autoComplete="comments"
            formikProps={formikProps}
          />
        </Form.Row>
        <Form.Row>
          <CheckBoxFormField
            controlId="neverExpires"
            label="Never Expires"
            formikProps={formikProps}
          />
          {values.id && (
            <React.Fragment>
              <CheckBoxFormField
                controlId="enabled"
                label="Enabled"
                formikProps={formikProps}
              />
              <CheckBoxFormField
                controlId="inactive"
                label="Inactive"
                formikProps={formikProps}
              />
              <CheckBoxFormField
                controlId="locked"
                label="Locked"
                formikProps={formikProps}
              />
            </React.Fragment>
          )}
        </Form.Row>
        <Form.Row>
          <Form.Group as={Col}>
            <Button
              className="EditAccount__set-password-button"
              appearance="contained"
              action="primary"
              type="button"
              // loading={okClicked}
              disabled={cancelClicked}
              onClick={onPasswordChange}
            >
              {values.id ? "Change Password" : "Set Password"}
            </Button>
          </Form.Group>
        </Form.Row>
      </Modal.Body>
      <Modal.Footer>
        <OkCancelButtons
          onOk={() => undefined}
          onCancel={onCancel}
          okClicked={isSubmitting}
          cancelClicked={cancelClicked}
        />
      </Modal.Footer>
    </Form>
  );
};

export const EditAccountFormik: React.FunctionComponent<EditAccountProps> = ({
  initialValues,
  onSubmit,
  onClose,
  onPasswordChange,
}) => {
  console.log("Render: EditAccountFormContainer");
  return (
    <Formik
      initialValues={initialValues}
      validationSchema={newAccountValidationSchema}
      onSubmit={onSubmit}
    >
      {(props) => {
        return (
          <EditAccountForm
            formikProps={props}
            okCancelProps={{ onCancel: () => onClose(false) }}
            changePasswordProps={{ onPasswordChange }}
          />
        );
      }}
    </Formik>
  );
};

export const EditAccount: React.FunctionComponent<{
  account: Account;
  passwordPolicyConfig: PasswordPolicyConfig;
  onClose: (success: boolean) => void;
}> = ({ account, passwordPolicyConfig, onClose }) => {
  const [showPasswordDialog, setShowPasswordDialog] = useState<boolean>();
  const [passwordState, setPasswordState] = useState<
    ChangePasswordFormValues
  >();

  const { create, update } = useAccountResource();

  const editPassword = () => {
    setShowPasswordDialog(true);
  };

  const onClosePasswordDialog = () => {
    setShowPasswordDialog(false);
  };

  const onSubmitPasswordChange = (
    values: ChangePasswordFormValues,
    actions: FormikHelpers<ChangePasswordFormValues>,
  ) => {
    setPasswordState(values);
    actions.setSubmitting(false);
    setShowPasswordDialog(false);
  };

  const onSubmit = (values, actions) => {
    const handleResponse = (response: any) => {
      if (!response) {
        actions.setSubmitting(false);
      } else {
        onClose(true);
      }
    };

    if (account.id === undefined) {
      const request: CreateAccountRequest = {
        firstName: values.firstName,
        lastName: values.lastName,
        userId: values.userId,
        email: values.email,
        comments: values.comments,
        password: passwordState && passwordState.password,
        confirmPassword: passwordState && passwordState.confirmPassword,
        forcePasswordChange: true,
        neverExpires: values.neverExpires,
      };
      create(request).then(handleResponse);
    } else {
      const request: UpdateAccountRequest = {
        account: {
          ...account,
          firstName: values.firstName,
          lastName: values.lastName,
          userId: values.userId,
          email: values.email,
          comments: values.comments,
          // forcePasswordChange: values.forcePasswordChange,
          neverExpires: values.neverExpires,
          enabled: values.enabled,
          inactive: values.inactive,
          locked: values.locked,
        },
        password: passwordState && passwordState.password,
        confirmPassword: passwordState && passwordState.confirmPassword,
      };
      update(request, account.id).then(handleResponse);
    }
  };

  return (
    <React.Fragment>
      <Dialog>
        <EditAccountFormik
          initialValues={account}
          onSubmit={onSubmit}
          onClose={onClose}
          onPasswordChange={editPassword}
        />
      </Dialog>

      {showPasswordDialog && (
        <ChangePasswordDialog
          title={account.id ? "Change Password" : "Set Password"}
          initialValues={{
            userId: account.userId,
            password: "",
            confirmPassword: "",
          }}
          passwordPolicyConfig={passwordPolicyConfig}
          onSubmit={onSubmitPasswordChange}
          onClose={onClosePasswordDialog}
        />
      )}
    </React.Fragment>
  );
};
