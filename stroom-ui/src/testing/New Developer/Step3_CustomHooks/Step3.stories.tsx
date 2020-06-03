import * as React from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import Step3 from "./Step3";

const stories = storiesOf("New Developer", module);

addThemedStories(stories, "Step 3", () => <Step3 />);
