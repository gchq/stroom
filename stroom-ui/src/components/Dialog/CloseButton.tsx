import { FunctionComponent } from "react";
import Button from "../Button/Button";
import * as React from "react";
export interface CloseProps {
  onClose: () => void;
}

export const CloseButton: FunctionComponent<CloseProps> = ({ onClose }) => {
  return (
    <div className="Button__container Dialog__buttons">
      <Button
        className="Dialog__button"
        appearance="contained"
        action="primary"
        icon="check"
        onClick={onClose}
      >
        Close
      </Button>
    </div>
  );
};
