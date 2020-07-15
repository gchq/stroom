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
import { Dialog } from "components/Dialog/Dialog";
import { OkCancelButtons, OkCancelProps } from "../../Dialog/OkCancelButtons";
import { Formik, FormikProps } from "formik";
import { Col, Form, Modal } from "react-bootstrap";
import { TextAreaFormField } from "components/FormField";
import { FormikHelpers } from "formik/dist/types";
import { newTokenValidationSchema } from "./validation";
import { CreateTokenRequest } from "../api/types";
import { FunctionComponent } from "react";
import { Token } from "../types";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useTokenResource } from "../api";
import CopyToClipboard from "react-copy-to-clipboard";
import Button from "../../Button/Button";

export interface EditTokenProps {
  initialValues: Token;
  onSubmit: (values: Token, actions: FormikHelpers<Token>) => void;
  onClose: (success: boolean) => void;
}

export interface EditTokenFormProps {
  formikProps: FormikProps<Token>;
  okCancelProps: OkCancelProps;
}

const EditTokenForm: FunctionComponent<EditTokenFormProps> = ({
  formikProps,
  okCancelProps,
}) => {
  const { values, handleChange, handleSubmit, isSubmitting } = formikProps;
  const { onCancel, cancelClicked } = okCancelProps;

  return (
    <Form noValidate={true} onSubmit={handleSubmit} className="EditToken">
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">
          <FontAwesomeIcon icon="key" className="mr-3" />
          Edit Token
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {/*<Form.Row>*/}
        {/*  <TextBoxField*/}
        {/*    type="text"*/}
        {/*    controlId="userId"*/}
        {/*    label="User Id"*/}
        {/*    placeholder="Enter A User Id"*/}
        {/*    onChange={handleChange}*/}
        {/*    onBlur={handleBlur}*/}
        {/*    value={values.userId}*/}
        {/*    error={errors.userId}*/}
        {/*    touched={touched.userId}*/}
        {/*    setFieldTouched={setFieldTouched}*/}
        {/*    autoFocus={true}*/}
        {/*    autoComplete="user-id"*/}
        {/*  />*/}
        {/*  <TextBoxField*/}
        {/*    type="text"*/}
        {/*    controlId="email"*/}
        {/*    label="Email"*/}
        {/*    placeholder="Enter An Email Address"*/}
        {/*    onChange={handleChange}*/}
        {/*    onBlur={handleBlur}*/}
        {/*    value={values.email}*/}
        {/*    error={errors.email}*/}
        {/*    touched={touched.email}*/}
        {/*    setFieldTouched={setFieldTouched}*/}
        {/*    autoComplete="email"*/}
        {/*  />*/}
        {/*</Form.Row>*/}
        {/*<Form.Row>*/}
        {/*  <TextBoxField*/}
        {/*    type="text"*/}
        {/*    controlId="firstName"*/}
        {/*    label="First Name"*/}
        {/*    placeholder="Enter First Name"*/}
        {/*    onChange={handleChange}*/}
        {/*    onBlur={handleBlur}*/}
        {/*    value={values.firstName}*/}
        {/*    error={errors.firstName}*/}
        {/*    touched={touched.firstName}*/}
        {/*    setFieldTouched={setFieldTouched}*/}
        {/*    autoComplete="first-name"*/}
        {/*  />*/}
        {/*  <TextBoxField*/}
        {/*    type="text"*/}
        {/*    controlId="lastName"*/}
        {/*    label="Last Name"*/}
        {/*    placeholder="Enter Last Name"*/}
        {/*    onChange={handleChange}*/}
        {/*    onBlur={handleBlur}*/}
        {/*    value={values.lastName}*/}
        {/*    error={errors.lastName}*/}
        {/*    touched={touched.lastName}*/}
        {/*    setFieldTouched={setFieldTouched}*/}
        {/*    autoComplete="last-name"*/}
        {/*  />*/}
        {/*</Form.Row>*/}
        <Form.Row>
          <TextAreaFormField
            controlId="data"
            label="Data"
            placeholder="Add Comments"
            autoComplete="data"
            formikProps={formikProps}
          />
          <CopyToClipboard text={values.data}>
            <Button
              appearance="contained"
              action="primary"
              type="button"
              icon="copy"
              text="Copy key"
            />
          </CopyToClipboard>
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
          {/*<Form.Group as={Col} controlId="neverExpires">*/}
          {/*  <Form.Check*/}
          {/*    contentEditable*/}
          {/*    type="checkbox"*/}
          {/*    label="Never Expires"*/}
          {/*    onChange={handleChange}*/}
          {/*    checked={values.neverExpires}*/}
          {/*  />*/}
          {/*</Form.Group>*/}
          <Form.Group as={Col} controlId="enabled">
            <Form.Check
              contentEditable
              type="checkbox"
              label="Enabled"
              onChange={handleChange}
              checked={values.enabled}
            />
          </Form.Group>
          {/*<Form.Group as={Col} controlId="inactive">*/}
          {/*  <Form.Check*/}
          {/*    contentEditable*/}
          {/*    type="checkbox"*/}
          {/*    label="Inactive"*/}
          {/*    onChange={handleChange}*/}
          {/*    checked={values.inactive}*/}
          {/*  />*/}
          {/*</Form.Group>*/}
          {/*<Form.Group as={Col} controlId="locked">*/}
          {/*  <Form.Check*/}
          {/*    contentEditable*/}
          {/*    type="checkbox"*/}
          {/*    label="Locked"*/}
          {/*    onChange={handleChange}*/}
          {/*    checked={values.locked}*/}
          {/*  />*/}
          {/*</Form.Group>*/}
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

export const EditTokenFormik: React.FunctionComponent<EditTokenProps> = ({
  initialValues,
  onSubmit,
  onClose,
}) => {
  console.log("Render: EditTokenFormContainer");
  return (
    <Formik
      initialValues={initialValues}
      validationSchema={newTokenValidationSchema}
      onSubmit={onSubmit}
    >
      {(props) => {
        return (
          <EditTokenForm
            formikProps={props}
            okCancelProps={{ onCancel: () => onClose(false) }}
          />
        );
      }}
    </Formik>
  );
};

export const EditToken: React.FunctionComponent<{
  token: Token;
  onClose: (success: boolean) => void;
}> = ({ token, onClose }) => {
  const { create, update } = useTokenResource();

  const onSubmit = (values, actions) => {
    const handleResponse = (response: any) => {
      if (!response) {
        actions.setSubmitting(false);
      } else {
        onClose(true);
      }
    };

    if (token.id === undefined) {
      const request: CreateTokenRequest = {
        userId: values.userId,
        tokenType: values.tokenType,
        expiresOnMs: values.expiresOnMs,
        comments: values.comments,
        enabled: values.enabled,
      };
      create(request).then(handleResponse);
    } else {
      const request: Token = {
        ...token,
        userId: values.userId,
        tokenType: values.tokenType,
        expiresOnMs: values.expiresOnMs,
        comments: values.comments,
        enabled: values.enabled,
      };
      update(request, request.id).then(handleResponse);
    }
  };

  return (
    <React.Fragment>
      <Dialog>
        <EditTokenFormik
          initialValues={token}
          onSubmit={onSubmit}
          onClose={onClose}
        />
      </Dialog>
    </React.Fragment>
  );
};
