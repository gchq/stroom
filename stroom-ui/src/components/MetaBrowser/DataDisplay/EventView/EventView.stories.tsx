import * as React from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import EventView from "./EventView";
import { eventData } from "testing/data/data";

const stories = storiesOf(
  "Sections/Meta Browser/Data Display",
  module,
);

addThemedStories(stories, "Event View", () => {
  return <EventView events={eventData.data} />;
});
