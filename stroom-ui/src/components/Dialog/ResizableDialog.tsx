import { FunctionComponent } from "react";
import * as React from "react";
import ReactModal from "react-modal-resizable-draggable";

export interface DialogProps {
  isOpen?: boolean;
  minWidth?: number;
  minHeight?: number;
  initWidth?: number;
  initHeight?: number;
  top?: number;
  left?: number;
  onRequestClose?: () => void;
  disableMove?: boolean;
  disableResize?: boolean;
  disableVerticalResize?: boolean;
  disableHorizontalResize?: boolean;
  disableVerticalMove?: boolean;
  disableHorizontalMove?: boolean;
  onFocus?: () => void;
  className?: string;
}

export const ResizableDialog: FunctionComponent<DialogProps> = (props) => {
  const p = {
    isOpen: true,
    // onRequestClose: () => undefined,
    // onFocus={() => console.log("Modal is clicked")}
    // className={"my-modal-custom-class"}
    // initWidth: 900,
    // initHeight: 400,
    ...props,
  };
  return (
    <ReactModal {...p} aria-labelledby="contained-modal-title-vcenter">
      <div className="modal-content">{props.children}</div>
    </ReactModal>
  );
};
