import * as React from "react";
import { storiesOf } from "@storybook/react";

import { addThemedStories } from "../../lib/themedStoryGenerator";
import Loader from "./Loader";
import StroomDecorator from "../../lib/storybook/StroomDecorator";

import "../../styles/main.css";

const stories = storiesOf("Loader", module).addDecorator(StroomDecorator);

addThemedStories(stories, <Loader message="Stuff is loading" />);
