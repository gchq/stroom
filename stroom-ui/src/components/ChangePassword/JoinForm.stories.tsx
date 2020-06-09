import { storiesOf } from "@storybook/react";
import * as React from "react";
import JoinForm from "./JoinForm";
import { addThemedStories } from "../../testing/storybook/themedStoryGenerator";

const stories = storiesOf("ChangePassword", module);
addThemedStories(stories, "JoinForm", () => <JoinForm />);
