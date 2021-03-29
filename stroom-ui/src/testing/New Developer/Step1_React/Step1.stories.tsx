// This must be at the top of every file with JSX in, otherwise you get the weird UMD error.
import * as React from "react";

// Basic storybook registration.
// There will be a bunch of default stuff for all stories globally registered in the .storybook/config.js file.
import { storiesOf } from "@storybook/react";

// Import the component under test
import Step1 from "./Step1";

// Stories can be organised into folders, so all these ones will be under 'New Developer'.
// All stories will be wrapped by the Stroom Decorator, which gives you the in browser dev server, CSS, hooks etc.
// Note: I have no idea where that 'module' thing comes from, sometimes it's best not to ask too many questions...
const stories = storiesOf("New Developer", module);

// This generates a 'theme-light' and 'theme-dark' story under our given location.
stories.add("Step 1", () => <Step1 />);
