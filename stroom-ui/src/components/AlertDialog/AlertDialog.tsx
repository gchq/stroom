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
import reactModalOptions from "../ThemedModal/reactModalOptions";
import { useTheme } from "../../lib/useTheme";
import { createRef } from "react";
import Button from "../Button/Button";

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
  alert?: Alert;
  isOpen: boolean;
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
  isOpen,
  onCloseDialog,
}) => {
  const { theme } = useTheme();
  const okButtonRef = createRef<HTMLButtonElement>();

  return (
    <ReactModal
      className={`themed-modal ${theme}`}
      style={reactModalOptions}
      isOpen={isOpen}
      appElement={document.body}
      shouldCloseOnOverlayClick={false}
      shouldCloseOnEsc={true}
      shouldFocusAfterRender={true}
      shouldReturnFocusAfterClose={true}
      onAfterOpen={() => okButtonRef.current.focus()}
    >
      <form onSubmit={() => onCloseDialog}>
        <div className="themed-modal__container">
          <header className="themed-modal__header">
            <AlertHeader {...alert} />
          </header>
          <div className="themed-modal__content">{alert && alert.message}</div>
          <div className="themed-modal__footer__actions">
            <Button
              appearance="contained"
              action="primary"
              icon="check"
              text="OK"
              type="submit"
              ref={okButtonRef}
            >
              OK
            </Button>
          </div>
        </div>
      </form>
    </ReactModal>
  );
};

// className="Button__ok"
// type="primary"
// htmlType="submit"
// ref={okButtonRef}

// // appearance="contained"
// // action="primary"
// // icon="check"
// // text="OK"
// // type="submit"
// ref={(button) => (this.__button = button)}

//
// /**
//  * These are the things returned by the custom hook that allow the owning component to interact
//  * with this dialog.
//  */
// interface UseDialog {
//   /**
//    * The owning component is ready to start a deletion process.
//    * Calling this will open the dialog, and setup the UUIDs
//    */
//   showDialog: () => void;
//   /**
//    * These are the properties that the owning component can just give to the Dialog component
//    * using destructing.
//    */
//   componentProps: Props;
// }
//
// /**
//  * This is a React custom hook that sets up things required by the owning component.
//  */
// export const useDialog = (): UseDialog => {
//   const [isOpen, setIsOpen] = React.useState<boolean>(false);
//
//   return {
//     componentProps: {
//       isOpen,
//       onCloseDialog: () => {
//         setIsOpen(false);
//       },
//     },
//     showDialog: () => {
//       setIsOpen(true);
//     },
//   };
// };

export default AlertDialog;
