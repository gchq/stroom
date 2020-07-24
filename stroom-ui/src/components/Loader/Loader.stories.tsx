import * as React from "react";
import { storiesOf } from "@storybook/react";

import Loader from "./Loader";

storiesOf("General Purpose", module).add("Loader", () => (
  <Loader message="Stuff is loading" />
));
