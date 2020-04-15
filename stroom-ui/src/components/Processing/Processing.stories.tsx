import * as React from "react";

import { storiesOf } from "@storybook/react";

import Processing from "./Processing";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";

const stories = storiesOf("Sections/Processing", module);

addThemedStories(stories, () => <Processing />);
