import * as React from "react";

import { storiesOf } from "@storybook/react";

import PillChoice from "./PillChoice";

storiesOf("New Developer/Step 2", module).add("Pill Choice", () => {
  return (
    <div>
      <h2>Pill Choice Test Harness</h2>
      <p>
        I often write a &apos;test harnes&apos; component which contains the
        simplest possible usage of a specific component.
      </p>
      <p>
        This one passes a callback down to Pill Choice that spits the choice out
        to the console. So now i&apos;s time to open the Chrome Developer tools
        and observe the console when the buttons are clicked.
      </p>
      <PillChoice onChoice={(p) => console.log("Pill Chosen", p)} />
    </div>
  );
});
