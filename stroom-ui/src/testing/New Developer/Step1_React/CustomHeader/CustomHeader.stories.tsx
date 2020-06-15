import * as React from "react";

import { storiesOf } from "@storybook/react";
import CustomHeader from ".";

storiesOf("New Developer/Step 1", module).add("Custom Header", () => (
  <CustomHeader title="Test Value" />
));
