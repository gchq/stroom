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
import { Formik, FormikProps } from "formik";
import { Col, Form, Modal } from "react-bootstrap";
import { FormikHelpers } from "formik/dist/types";
import { newTokenValidationSchema } from "./validation";
import { FunctionComponent } from "react";
import { Token } from "../types";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useTokenResource } from "../api";
import CopyToClipboard from "react-copy-to-clipboard";
import Button from "../../Button/Button";
import { FormField } from "../../FormField/FormField";
import { CloseButton, CloseProps } from "../../Dialog/CloseButton";
import useDateUtil from "../../../lib/useDateUtil";

export interface EditTokenProps {
  initialValues: Token;
  onSubmit: (values: Token, actions: FormikHelpers<Token>) => void;
  onClose: (success: boolean) => void;
}

export interface EditTokenFormProps {
  formikProps: FormikProps<Token>;
  closeProps: CloseProps;
}

const EditTokenForm: FunctionComponent<EditTokenFormProps> = ({
  formikProps,
  closeProps,
}) => {
  const { values, handleSubmit } = formikProps;
  const { onClose } = closeProps;

  const { toggleEnabled } = useTokenResource();
  const { toDateString } = useDateUtil();

  return (
    <Form noValidate={true} onSubmit={handleSubmit} className="EditToken">
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">
          <FontAwesomeIcon icon="key" className="mr-3" />
          Edit API Key
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form.Row>
          <Form.Group as={Col} controlId="enabled">
            <Form.Check
              contentEditable
              type="checkbox"
              label="Enabled"
              onChange={() => {
                toggleEnabled(values.id, !values.enabled).then(() => {
                  formikProps.setFieldValue("enabled", !values.enabled, true);
                });
              }}
              checked={values.enabled}
            />
          </Form.Group>
        </Form.Row>
        <Form.Row>
          <Form.Group as={Col} controlId="userId">
            <Form.Label>Issued To: {values.userId}</Form.Label>
          </Form.Group>
        </Form.Row>
        <Form.Row>
          <Form.Group as={Col} controlId="expiresOnMs">
            <Form.Label>
              Expires On: {toDateString(values.expiresOnMs)}
            </Form.Label>
          </Form.Group>
        </Form.Row>
        <Form.Row>
          <FormField controlId="data" label="API Key" error="">
            <Form.Control
              as="textarea"
              rows={5}
              value={values.data}
              readOnly={true}
            />
            <div className="EditToken__copyButtonContainer">
              <CopyToClipboard text={values.data}>
                <Button
                  appearance="contained"
                  action="primary"
                  type="button"
                  icon="copy"
                >
                  Copy key
                </Button>
              </CopyToClipboard>
            </div>
          </FormField>
        </Form.Row>
        <Form.Row>
          <FormField controlId="comments" label="Comments" error="">
            <Form.Control
              as="textarea"
              rows={5}
              value={values.comments}
              readOnly={true}
            />
          </FormField>
        </Form.Row>
      </Modal.Body>
      <Modal.Footer>
        <CloseButton onClose={onClose} />
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
            closeProps={{ onClose: () => onClose(false) }}
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
  const onSubmit = () => {
    onClose(true);
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
