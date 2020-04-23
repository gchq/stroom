import * as React from "react";
import { storiesOf } from "@storybook/react";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import CustomLoader from "./CustomLoader";

const stories = storiesOf("General Purpose/Loader", module);

addThemedStories(stories, () => <CustomLoader title="Stroom" message="Stuff is loading" />);
