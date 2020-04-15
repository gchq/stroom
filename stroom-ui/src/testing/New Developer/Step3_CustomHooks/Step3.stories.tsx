import * as React from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import Step3 from "./Step3";

const stories = storiesOf("New Developer/Step 3", module);

addThemedStories(stories, () => <Step3 />);
