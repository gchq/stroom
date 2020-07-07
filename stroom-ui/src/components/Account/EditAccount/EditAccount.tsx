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
import { TextBoxField } from "../../Form/TextBoxField";
import { Dialog } from "components/Dialog/Dialog";
import { PersonFill } from "react-bootstrap-icons";
import { OkCancelButtons, OkCancelProps } from "../../Dialog/OkCancelButtons";
import { Formik, FormikProps } from "formik";
import { Form, Modal } from "react-bootstrap";
import { TextAreaField } from "../../Form/TextAreaField";
import { newAccountValidationSchema } from "./validation";
import { CreateAccountRequest } from "../api/types";
import useAccountResource from "../api/useAccountResource";
import { Account } from "components/Account/types";

// export interface EditAccountFormValues {
//   userId: string;
//   email: string;
//   firstName: string;
//   lastName: string;
//   comments: string;
// }

export const EditAccountForm: React.FunctionComponent<
  FormikProps<Account> & OkCancelProps
> = ({
  values,
  errors,
  touched,
  setFieldTouched,
  handleChange,
  handleBlur,
  handleSubmit,
  isSubmitting,
  onCancel,
  cancelClicked,
}) => (
  <Form noValidate={true} onSubmit={handleSubmit} className="EditAccount">
    <Modal.Header closeButton={false}>
      <Modal.Title id="contained-modal-title-vcenter">
        <PersonFill className="mr-3" />
        Edit Account
      </Modal.Title>
    </Modal.Header>
    <Modal.Body>
      <Form.Row>
        <TextBoxField
          type="text"
          controlId="userId"
          label="User Id"
          placeholder="Enter A User Id"
          onChange={handleChange}
          onBlur={handleBlur}
          value={values.userId}
          error={errors.userId}
          touched={touched.userId}
          setFieldTouched={setFieldTouched}
          autoFocus={true}
          autoComplete="user-id"
        />
        <TextBoxField
          type="text"
          controlId="email"
          label="Email"
          placeholder="Enter An Email Address"
          onChange={handleChange}
          onBlur={handleBlur}
          value={values.email}
          error={errors.email}
          touched={touched.email}
          setFieldTouched={setFieldTouched}
          autoComplete="email"
        />
      </Form.Row>
      <Form.Row>
        <TextBoxField
          type="text"
          controlId="firstName"
          label="First Name"
          placeholder="Enter First Name"
          onChange={handleChange}
          onBlur={handleBlur}
          value={values.firstName}
          error={errors.firstName}
          touched={touched.firstName}
          setFieldTouched={setFieldTouched}
          autoComplete="first-name"
        />
        <TextBoxField
          type="text"
          controlId="lastName"
          label="Last Name"
          placeholder="Enter Last Name"
          onChange={handleChange}
          onBlur={handleBlur}
          value={values.lastName}
          error={errors.lastName}
          touched={touched.lastName}
          setFieldTouched={setFieldTouched}
          autoComplete="last-name"
        />
      </Form.Row>
      <Form.Row>
        <TextAreaField
          controlId="comments"
          label="Comments"
          placeholder="Add Comments"
          onChange={handleChange}
          onBlur={handleBlur}
          value={values.comments}
          error={errors.comments}
          touched={touched.comments}
          setFieldTouched={setFieldTouched}
          autoComplete="comments"
        />
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

const EditAccountFormik: React.FunctionComponent<{
  account: Account;
  onClose: (success: boolean) => void;
}> = (props) => {
  console.log("Render: EditAccountFormContainer");

  const onCancel = () => {
    props.onClose(false);
  };

  const { create, update } = useAccountResource();

  return (
    <Formik
      initialValues={props.account}
      validationSchema={newAccountValidationSchema}
      onSubmit={(values, actions) => {
        const handleResponse = (response: any) => {
          if (!response) {
            actions.setSubmitting(false);
          } else {
            props.onClose(true);
          }
        };

        if (props.account.id === undefined) {
          const request: CreateAccountRequest = {
            firstName: values.firstName,
            lastName: values.lastName,
            userId: values.userId,
            email: values.email,
            comments: values.comments,
            password: undefined,
            confirmPassword: undefined,
            forcePasswordChange: values.forcePasswordChange,
            neverExpires: values.neverExpires,
          };
          create(request).then(handleResponse);
        } else {
          const account: Account = {
            ...props.account,
            firstName: values.firstName,
            lastName: values.lastName,
            userId: values.userId,
            email: values.email,
            comments: values.comments,
            forcePasswordChange: true,
            neverExpires: false,
          };
          update(account, account.id).then(handleResponse);
        }
      }}
    >
      {(props) => {
        return <EditAccountForm {...props} onCancel={onCancel} />;
      }}
    </Formik>
  );
};

export const EditAccount: React.FunctionComponent<{
  account: Account;
  onClose: (success: boolean) => void;
}> = (props) => {
  return (
    <Dialog>
      <EditAccountFormik {...props} />
    </Dialog>
  );
};
