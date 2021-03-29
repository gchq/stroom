import * as React from "react";
import { storiesOf } from "@storybook/react";
import MinimalInput from "./MinimalInput";

storiesOf("General Purpose", module).add("Minimal Input", () => (
  <div style={{ padding: "1em" }}>
    <MinimalInput />
  </div>
));
