import * as React from "react";
import { storiesOf } from "@storybook/react";
import Sidebar from "./Sidebar";

storiesOf("App Chrome", module).add("Sidebar", () => (
  <Sidebar activeMenuItem="welcome" />
));
