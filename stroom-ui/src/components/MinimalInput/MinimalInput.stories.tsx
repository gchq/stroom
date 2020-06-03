import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import MinimalInput from "./MinimalInput";

const stories = storiesOf("General Purpose", module);

addThemedStories(stories, "Minimal Input", () => (
  <div style={{ padding: "1em" }}>
    <MinimalInput />
  </div>
));
