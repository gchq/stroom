import { storiesOf } from "@storybook/react";
import * as React from "react";
import { Pager } from "./Pager";

const stories = storiesOf("Pager", module);
stories.add("Pager", () => {
  return <Pager page={{ from: 1, to: 10, of: 1000 }} />;
});
