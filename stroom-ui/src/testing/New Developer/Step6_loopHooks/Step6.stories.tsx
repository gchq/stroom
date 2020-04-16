import * as React from "react";
import { storiesOf } from "@storybook/react";
import Step6 from "./Step6";

storiesOf("New Developer/Step 6", module).add("test", () => {
  const onNameClick = React.useCallback(
    (name: string) => console.log(`Name Clicked: ${name}`),
    [],
  );

  return (
    <Step6 onNameClick={onNameClick} names={["Harry", "Hermione", "Ron"]} />
  );
});
