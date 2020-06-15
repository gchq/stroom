import * as React from "react";
import { storiesOf } from "@storybook/react";

import DialogActionButtons from "./DialogActionButtons";

const TestHarness: React.FunctionComponent = () => {
  const [hasConfirmed, setHasConfirmed] = React.useState<boolean>(false);
  const [hasCancelled, setHasCancelled] = React.useState<boolean>(false);

  const onCancel = React.useCallback(() => {
    setHasCancelled(true);
  }, [setHasCancelled]);
  const onConfirm = React.useCallback(() => {
    setHasConfirmed(true);
  }, [setHasConfirmed]);
  const onReset = React.useCallback(() => {
    setHasConfirmed(false);
    setHasCancelled(false);
  }, [setHasConfirmed, setHasCancelled]);

  return (
    <div>
      <DialogActionButtons onCancel={onCancel} onConfirm={onConfirm} />
      <form>
        <div>
          <label>Has Confirmed</label>
          <input type="checkbox" readOnly checked={hasConfirmed} />
        </div>
        <div>
          <label>Has Cancelled</label>
          <input type="checkbox" readOnly checked={hasCancelled} />
        </div>
      </form>
      <button onClick={onReset}>Reset</button>
    </div>
  );
};

storiesOf("General Purpose/Dialog Action Buttons", module).add("simple", () => (
  <TestHarness />
));
