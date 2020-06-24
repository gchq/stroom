import { FunctionComponent } from "react";
import Button from "../Button/Button";
import * as React from "react";
import { OkCancelProps } from "./ConfirmCurrentPassword";

const OkCancelButtons: FunctionComponent<OkCancelProps> = ({
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

export default OkCancelButtons;
