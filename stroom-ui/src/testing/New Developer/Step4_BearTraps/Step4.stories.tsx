import * as React from "react";

import { storiesOf } from "@storybook/react";
import Step4, { Car } from "./Step4";

const stories = storiesOf("New Developer", module);

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

stories.add("Step 4", () => (
  <Step4 cars={CARS} loadCars={() => console.log("Loading Cars")} />
));
