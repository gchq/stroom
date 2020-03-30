import * as React from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import EventView from "./EventView";
import { eventData } from "testing/data/data";

const stories = storiesOf(
  "Sections/Meta Browser/Data Display/Event View",
  module,
);

addThemedStories(stories, () => {
  return <EventView events={eventData.data} />;
});
