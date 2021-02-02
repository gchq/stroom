import { Modal, ModalProps } from "react-bootstrap";
import { FunctionComponent } from "react";
import * as React from "react";

export interface DialogProps extends ModalProps {
  show?: boolean;
}

export const Dialog: FunctionComponent<DialogProps> = (props) => {
  const p = {
    show: true,
    onHide: () => undefined,
    centered: true,
    ...props,
  };
  return (
    <Modal {...p} aria-labelledby="contained-modal-title-vcenter">
      {props.children}
    </Modal>
  );
};
