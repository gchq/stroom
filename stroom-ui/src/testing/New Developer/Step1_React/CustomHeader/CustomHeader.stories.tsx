import * as React from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "../../../storybook/themedStoryGenerator";
import CustomHeader from ".";

const stories = storiesOf("New Developer/Step 1", module);

addThemedStories(stories, "Custom Header", () => <CustomHeader title="Test Value" />);
