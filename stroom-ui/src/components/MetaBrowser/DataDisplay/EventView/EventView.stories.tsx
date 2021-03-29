import * as React from "react";

import { storiesOf } from "@storybook/react";
import EventView from "./EventView";
import { eventData } from "testing/data/data";

storiesOf("Sections/Meta Browser/Data Display", module).add(
  "Event View",
  () => {
    return <EventView events={eventData.data} />;
  },
);
