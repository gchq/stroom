import * as React from "react";
import { storiesOf } from "@storybook/react";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import Loader from "./Loader";

const stories = storiesOf("General Purpose/Loader", module);

addThemedStories(stories, () => <Loader message="Stuff is loading" />);
