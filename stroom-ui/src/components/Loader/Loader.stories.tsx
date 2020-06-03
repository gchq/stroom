import * as React from "react";
import { storiesOf } from "@storybook/react";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import Loader from "./Loader";

const stories = storiesOf("General Purpose", module);

addThemedStories(stories, "Loader", () => <Loader message="Stuff is loading" />);
