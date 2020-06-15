import * as React from "react";
import { storiesOf } from "@storybook/react";

import CustomLoader from "./CustomLoader";

storiesOf("General Purpose", module).add("Loader", () => (
  <CustomLoader title="Stroom" message="Stuff is loading" />
));
