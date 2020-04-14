import * as React from "react";

import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import Step4, { Car } from "./Step4";

const stories = storiesOf("New Developer/Step 4", module);

const CARS: Car[] = [
  {
    model: "Yaris",
    manufacturer: "Toyota",
  },
  {
    model: "Focus",
    manufacturer: "Ford",
  },
  {
    model: "Model-T",
    manufacturer: "Tesla",
  },
];

addThemedStories(stories, () => (
  <Step4 cars={CARS} loadCars={() => console.log("Loading Cars")} />
));
