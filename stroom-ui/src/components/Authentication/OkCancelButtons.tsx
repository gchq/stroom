import { FunctionComponent } from "react";
import Button from "../Button/Button";
import * as React from "react";

export interface OkCancelProps {
  onOk?: () => void;
  onCancel?: () => void;
  okClicked?: boolean;
  cancelClicked?: boolean;
}

export const OkCancelButtons: FunctionComponent<OkCancelProps> = ({
  onOk,
  onCancel,
  okClicked,
  cancelClicked,
}: OkCancelProps) => {
  return (
    <div className="OkCancelButtons page__buttons Button__container">
      <Button
        className="OkCancelButtons__ok"
        appearance="contained"
        action="primary"
        icon="check"
        type="submit"
        loading={okClicked}
        disabled={cancelClicked}
        onClick={onOk}
      >
        OK
      </Button>
      <Button
        className="OkCancelButtons__cancel"
        appearance="contained"
        action="secondary"
        icon="times"
        type="button"
        loading={cancelClicked}
        disabled={okClicked}
        onClick={onCancel}
      >
        Cancel
      </Button>
    </div>
  );
};
