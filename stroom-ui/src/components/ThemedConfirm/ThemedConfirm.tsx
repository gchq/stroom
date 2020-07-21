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
import ReactModal from "react-modal";

import Button from "../Button";
import IconHeader from "../IconHeader";
import reactModalOptions from "../ThemedModal/reactModalOptions";
import { useTheme } from "lib/useTheme/useTheme";

interface NewProps {
  getQuestion: () => string;
  getDetails?: () => string;
  onConfirm: () => void;
}

interface Props extends ReactModal.Props {
  question: string;
  details?: string;
  onConfirm: () => void;
  onCloseDialog: () => void;
}

const ThemedConfirm: React.FunctionComponent<Props> = ({
  question,
  details,
  onCloseDialog,
  onConfirm,
  ...rest
}) => {
  const { theme } = useTheme();

  return (
    <ReactModal
      className={`themed-modal ${theme}`}
      {...rest}
      style={reactModalOptions}
    >
      <div className="themed-modal__container">
        <header className="themed-modal__header">
          <IconHeader text={question} icon="question-circle" />
        </header>
        {details && <div className="themed-modal__content">{details}</div>}
        <div className="themed-modal__footer__actions">
          <Button icon="times" onClick={onCloseDialog}>
            Cancel
          </Button>
          <Button
            onClick={() => {
              onConfirm();
              onCloseDialog();
            }}
            icon="check"
          >
            Confirm
          </Button>
        </div>
      </div>
    </ReactModal>
  );
};

interface UseDialog {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showDialog: () => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: Props;
}

/**
 * This is a React custom hook that sets up things required by the owning component.
 */
export const useDialog = (props: NewProps): UseDialog => {
  const [isOpen, setIsOpen] = React.useState<boolean>(false);
  const [question, setQuestion] = React.useState<string>("No Question");
  const [details, setDetails] = React.useState<string | undefined>(undefined);

  const { getQuestion, getDetails = (): string => undefined } = props;

  return {
    componentProps: {
      question,
      details,
      isOpen,
      onConfirm: props.onConfirm,
      onCloseDialog: () => {
        setIsOpen(false);
      },
    },
    showDialog: () => {
      setQuestion(getQuestion());
      setDetails(getDetails());
      setIsOpen(true);
    },
  };
};

export default ThemedConfirm;
