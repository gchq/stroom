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
import { CreateTokenRequest, TokenConfig } from "../api/types";
import { FunctionComponent, useEffect, useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useTokenResource } from "../api";
import { DatePickerFormField, UserFormField } from "components/FormField";
import CustomLoader from "../../CustomLoader";
import moment from "moment";

export interface CreateTokenFormValues {
  expiresOnMs: number;
  userId: string;
}

export interface CreateTokenProps {
  initialValues: CreateTokenFormValues;
  onSubmit: (
    values: CreateTokenFormValues,
    actions: FormikHelpers<CreateTokenFormValues>,
  ) => void;
  onClose: (success: boolean) => void;
}

export interface CreateTokenFormProps {
  formikProps: FormikProps<CreateTokenFormValues>;
  okCancelProps: OkCancelProps;
}

const CreateTokenForm: FunctionComponent<CreateTokenFormProps> = ({
  formikProps,
  okCancelProps,
}) => {
  const { handleSubmit, isSubmitting } = formikProps;
  const { onCancel, cancelClicked } = okCancelProps;

  return (
    <Form noValidate={true} onSubmit={handleSubmit} className="CreateToken">
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">
          <FontAwesomeIcon icon="key" className="mr-3" />
          Create API Key
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form.Row>
          <DatePickerFormField
            controlId="expiresOnMs"
            label="Expiry date"
            placeholder="Choose Expiry Date"
            formikProps={formikProps}
          />
        </Form.Row>
        <Form.Row>
          <UserFormField
            controlId="userId"
            label="User Id"
            placeholder="Select User"
            autoComplete="userId"
            formikProps={formikProps}
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
    <Formik<CreateTokenFormValues>
      initialValues={initialValues}
      validationSchema={newTokenValidationSchema}
      onSubmit={onSubmit}
    >
      {(formikProps) => {
        return (
          <CreateTokenForm
            formikProps={formikProps}
            okCancelProps={{ onCancel: () => onClose(false) }}
          />
        );
      }}
    </Formik>
  );
};

export const CreateToken: React.FunctionComponent<{
  onClose: (success: boolean) => void;
}> = ({ onClose }) => {
  const { create, fetchTokenConfig } = useTokenResource();

  const [tokenConfig, setTokenConfig] = useState<TokenConfig>();
  useEffect(() => {
    if (tokenConfig === undefined) {
      fetchTokenConfig().then((conf) => setTokenConfig(conf));
    }
  }, [tokenConfig, setTokenConfig, fetchTokenConfig]);

  const onSubmit = (
    values: CreateTokenFormValues,
    actions: FormikHelpers<CreateTokenFormValues>,
  ) => {
    //   setPasswordState(values);
    //   actions.setSubmitting(false);
    //   setShowPasswordDialog(false);
    // };
    //
    // const onSubmit = (values, actions) => {
    const handleResponse = (response: any) => {
      if (!response) {
        actions.setSubmitting(false);
      } else {
        onClose(true);
      }
    };

    const request: CreateTokenRequest = {
      userId: values.userId,
      tokenType: "api",
      expiresOnMs: values.expiresOnMs,
      comments: undefined,
      enabled: true,
    };
    create(request).then(handleResponse);
  };

  if (tokenConfig === undefined) {
    return (
      <CustomLoader title="Stroom" message="Loading Config. Please wait..." />
    );
  }

  const expiresOnMs = moment()
    .add(tokenConfig.defaultApiKeyExpiryInMinutes, "minutes")
    .valueOf();

  return (
    <Dialog>
      <CreateTokenFormik
        initialValues={{
          expiresOnMs: expiresOnMs,
          userId: undefined,
        }}
        onSubmit={onSubmit}
        onClose={onClose}
      />
    </Dialog>
  );
};
