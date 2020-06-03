import * as React from "react";
import { storiesOf } from "@storybook/react";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import CustomLoader from "./CustomLoader";

const stories = storiesOf("General Purpose", module);

addThemedStories(stories, "Loader", () => <CustomLoader title="Stroom" message="Stuff is loading" />);
