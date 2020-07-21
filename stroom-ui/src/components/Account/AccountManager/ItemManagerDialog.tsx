import * as React from "react";
import { PropsWithChildren, ReactElement, ReactNode } from "react";
import { ItemManager, ItemManagerProps } from "./ItemManager";
import { Dialog } from "../../Dialog/Dialog";
import { Modal } from "react-bootstrap";
import Button from "../../Button/Button";

export interface ItemManagerDialogProps<T> {
  title: ReactNode;
  itemManagerProps: ItemManagerProps<T>;
  onClose: () => void;
}

export const ItemManagerDialog = <T,>(
  props: PropsWithChildren<ItemManagerDialogProps<T>>,
): ReactElement<any, any> | null => {
  const { title, itemManagerProps, onClose } = props;

  return (
    <Dialog>
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">{title}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <ItemManager {...itemManagerProps} />
      </Modal.Body>
      <Modal.Footer>
        <Button
          appearance="contained"
          action="primary"
          icon="check"
          onClick={onClose}
        >
          Close
        </Button>
      </Modal.Footer>
    </Dialog>
  );
};
