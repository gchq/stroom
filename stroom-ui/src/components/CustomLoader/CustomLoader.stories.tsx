import * as React from "react";
import { storiesOf } from "@storybook/react";

import CustomLoader from "./CustomLoader";

storiesOf("Custom Loader", module).add("Loader", () => (
  <CustomLoader title="Stroom" message="Stuff is loading" />
));
