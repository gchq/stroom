import * as React from "react";
import { storiesOf } from "@storybook/react";

import OkButtons from "./OkButtons";

const TestHarness: React.FunctionComponent = () => {
  const [ok, setOk] = React.useState<boolean>(false);

  const onOk = React.useCallback(() => {
    setOk(true);
  }, [setOk]);
  const onReset = React.useCallback(() => {
    setOk(false);
  }, [setOk]);

  return (
    <div>
      <OkButtons onOk={onOk}/>
      <form>
        <div>
          <label>Ok?</label>
          <input type="checkbox" readOnly checked={ok}/>
        </div>
      </form>
      <button onClick={onReset}>Reset</button>
    </div>
  );
};

storiesOf("General Purpose/Ok Button", module).add("simple", () => (
  <TestHarness/>
));
