/*
 * Copyright 2018 Crown Copyright
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
import Button from "../Button/Button";
import { Form, Modal } from "react-bootstrap";
import { Dialog } from "components/Dialog/Dialog";

export enum AlertType {
  INFO,
  WARNING,
  ERROR,
  FATAL,
}

export interface Alert {
  type: AlertType;
  title: string;
  message: string;
}

interface Props {
  alert: Alert;
  onCloseDialog: () => void;
}

interface ImageProps {
  text: string;
  imageSrc: string;
}

const ImageHeader: React.FunctionComponent<ImageProps> = ({
  text,
  imageSrc,
}) => (
  <div className="ImageHeader">
    <img className="ImageHeader__icon" alt={text} title={text} src={imageSrc} />
    <div className="ImageHeader__text">{text}</div>
  </div>
);

const AlertHeader: React.FunctionComponent<Alert> = (alert) => {
  switch (alert.type) {
    case AlertType.INFO: {
      return (
        <ImageHeader
          imageSrc={require("../../images/alert/info.svg")}
          text={alert.title}
        />
      );
    }
    case AlertType.WARNING: {
      return (
        <ImageHeader
          imageSrc={require("../../images/alert/warning.svg")}
          text={alert.title}
        />
      );
    }
    case AlertType.ERROR: {
      return (
        <ImageHeader
          imageSrc={require("../../images/alert/error.svg")}
          text={alert.title}
        />
      );
    }
    case AlertType.FATAL: {
      return (
        <ImageHeader
          imageSrc={require("../../images/alert/fatal.svg")}
          text={alert.title}
        />
      );
    }
    default: {
      return (
        <ImageHeader
          imageSrc={require("../../images/alert/error.svg")}
          text={alert.title}
        />
      );
    }
  }
};

export const AlertDialog: React.FunctionComponent<Props> = ({
  alert,
  onCloseDialog,
}) => {
  return (
    <Dialog>
      <Form
        noValidate={true}
        onSubmit={(event) => {
          event.preventDefault();
          onCloseDialog();
        }}
      >
        <Modal.Header closeButton={false}>
          <AlertHeader {...alert} />
        </Modal.Header>
        <Modal.Body>{alert && alert.message} </Modal.Body>
        <Modal.Footer>
          <Button
            className="Button__ok"
            appearance="contained"
            action="primary"
            icon="check"
            type="submit"
            autoFocus={true}
          >
            OK
          </Button>
        </Modal.Footer>
      </Form>
    </Dialog>
  );
};

export default AlertDialog;
