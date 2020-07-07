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

export enum PromptType {
  INFO,
  WARNING,
  ERROR,
  FATAL,
}

export interface ContentProps {
  title?: string;
  message?: string;
}

export interface PromptProps extends ContentProps {
  type: PromptType;
}

interface Props {
  promptProps: PromptProps;
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

const PromptHeader: React.FunctionComponent<PromptProps> = (prompt) => {
  switch (prompt.type) {
    case PromptType.INFO: {
      return (
        <ImageHeader
          imageSrc={require("../../images/prompt/info.svg")}
          text={prompt.title}
        />
      );
    }
    case PromptType.WARNING: {
      return (
        <ImageHeader
          imageSrc={require("../../images/prompt/warning.svg")}
          text={prompt.title}
        />
      );
    }
    case PromptType.ERROR: {
      return (
        <ImageHeader
          imageSrc={require("../../images/prompt/error.svg")}
          text={prompt.title}
        />
      );
    }
    case PromptType.FATAL: {
      return (
        <ImageHeader
          imageSrc={require("../../images/prompt/fatal.svg")}
          text={prompt.title}
        />
      );
    }
    default: {
      return (
        <ImageHeader
          imageSrc={require("../../images/prompt/error.svg")}
          text={prompt.title}
        />
      );
    }
  }
};

export const Prompt: React.FunctionComponent<Props> = ({
  promptProps,
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
          <PromptHeader {...promptProps} />
        </Modal.Header>
        <Modal.Body>{promptProps && promptProps.message} </Modal.Body>
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

export default Prompt;
