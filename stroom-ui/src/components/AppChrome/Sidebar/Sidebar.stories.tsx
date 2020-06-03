import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import Sidebar from "./Sidebar";

const stories = storiesOf("App Chrome", module);

addThemedStories(stories, "Sidebar", () => <Sidebar activeMenuItem="welcome" />);
