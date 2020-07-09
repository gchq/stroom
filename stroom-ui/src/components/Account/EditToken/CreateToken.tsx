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
import { Form, Modal } from "react-bootstrap";
import { FormikHelpers } from "formik/dist/types";
import { newTokenValidationSchema } from "./validation";
import { CreateTokenRequest } from "../api/types";
import { FunctionComponent } from "react";
import { Token } from "../types";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useTokenResource } from "../api";
import { DatePickerFormField, UserFormField } from "components/FormField";

export interface CreateTokenProps {
  initialValues: Token;
  onSubmit: (values: Token, actions: FormikHelpers<Token>) => void;
  onClose: (success: boolean) => void;
}

export interface CreateTokenFormProps {
  formikProps: FormikProps<Token>;
  okCancelProps: OkCancelProps;
}

const CreateTokenForm: FunctionComponent<CreateTokenFormProps> = ({
  formikProps,
  okCancelProps,
}) => {
  const {
    values,
    errors,
    touched,
    setFieldTouched,
    handleChange,
    handleBlur,
    handleSubmit,
    isSubmitting,
  } = formikProps;
  const { onCancel, cancelClicked } = okCancelProps;

  return (
    <Form noValidate={true} onSubmit={handleSubmit} className="CreateToken">
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">
          <FontAwesomeIcon icon="key" className="mr-3" />
          Create Token
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form.Row>
          <DatePickerFormField
            controlId="expiryDate"
            label="Expiry date"
            placeholder="Choose Expiry Date"
            onChange={handleChange}
            onBlur={handleBlur}
            value={values.expiresOnMs}
            error={errors.expiresOnMs}
            touched={touched.expiresOnMs}
            setFieldTouched={setFieldTouched}
          />
        </Form.Row>
        <Form.Row>
          <UserFormField
            controlId="userId"
            label="User Id"
            placeholder="Select User"
            onChange={handleChange}
            onBlur={handleBlur}
            value={values.userId}
            error={errors.userId}
            touched={touched.userId}
            setFieldTouched={setFieldTouched}
            autoComplete="userId"
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
};

export const CreateTokenFormik: React.FunctionComponent<CreateTokenProps> = ({
  initialValues,
  onSubmit,
  onClose,
}) => {
  console.log("Render: CreateTokenFormContainer");
  return (
    <Formik
      initialValues={initialValues}
      validationSchema={newTokenValidationSchema}
      onSubmit={onSubmit}
    >
      {(props) => {
        return (
          <CreateTokenForm
            formikProps={props}
            okCancelProps={{ onCancel: () => onClose(false) }}
          />
        );
      }}
    </Formik>
  );
};

export const CreateToken: React.FunctionComponent<{
  token: Token;
  onClose: (success: boolean) => void;
}> = ({ token, onClose }) => {
  const { create } = useTokenResource();

  const onSubmit = (values, actions) => {
    const handleResponse = (response: any) => {
      if (!response) {
        actions.setSubmitting(false);
      } else {
        onClose(true);
      }
    };

    const request: CreateTokenRequest = {
      userId: values.userId,
      tokenType: values.tokenType,
      expiresOnMs: values.expiresOnMs,
      comments: values.comments,
      enabled: values.enabled,
    };
    create(request).then(handleResponse);
  };

  return (
    <React.Fragment>
      <Dialog>
        <CreateTokenFormik
          initialValues={token}
          onSubmit={onSubmit}
          onClose={onClose}
        />
      </Dialog>
    </React.Fragment>
  );
};
