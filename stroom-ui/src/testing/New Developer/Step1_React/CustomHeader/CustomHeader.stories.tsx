import * as React from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "../../../storybook/themedStoryGenerator";
import CustomHeader from ".";

const stories = storiesOf("New Developer/Step 1/Custom Header", module);

addThemedStories(stories, () => <CustomHeader title="Test Value" />);
