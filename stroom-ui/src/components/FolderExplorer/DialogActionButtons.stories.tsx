import * as React from "react";
import { compose } from "recompose";
import { withStateHandlers } from "recompose";
import { storiesOf } from "@storybook/react";

import DialogActionButtons from "./DialogActionButtons";
import StroomDecorator from "../../lib/storybook/StroomDecorator";

import "../../styles/main.css";

interface Props {}
interface WithStateHandlers {
  hasConfirmed: boolean;
  hasCancelled: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  onReset: () => void;
}
interface EnhancedProps extends Props, WithStateHandlers {}

const enhance = compose<EnhancedProps, Props>(
  withStateHandlers(({}) => ({ hasConfirmed: false, hasCancelled: false }), {
    onCancel: () => () => ({ hasCancelled: true }),
    onConfirm: () => () => ({ hasConfirmed: true }),
    onReset: () => () => ({ hasConfirmed: false, hasCancelled: false })
  })
);

let TestHarness = enhance(
  ({
    hasConfirmed,
    hasCancelled,
    onCancel,
    onConfirm,
    onReset
  }: EnhancedProps) => (
    <div>
      <DialogActionButtons onCancel={onCancel} onConfirm={onConfirm} />
      <form>
        <div>
          <label>Has Confirmed</label>
          <input type="checkbox" checked={hasConfirmed} onChange={() => {}} />
        </div>
        <div>
          <label>Has Cancelled</label>
          <input type="checkbox" checked={hasCancelled} onChange={() => {}} />
        </div>
      </form>
      <button onClick={onReset}>Reset</button>
    </div>
  )
);

storiesOf("Explorer/Dialog Action Buttons", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => <TestHarness />);
