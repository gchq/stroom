import * as React from "react";

import PillChoice from "./PillChoice";
import DisplayPillChoice from "./DisplayPillChoice";

export const Step2 = () => {
  /**
   * This is our first use of state. This component will track a value called
   * 'pill' and useState returns this 'array', the first item is the value, the
   * second item is a function to update that value. Later we will show the use
   * of optional values that can be 'undefined'. In TypeScript you must be
   * explicit about this. In this example I am seeding the state with a default
   * value which is a valid string.
   */
  const [pill, setPill] = React.useState<string>("none");

  return (
    <div>
      <h2>Step 2 - Callbacks and State</h2>

      <p>This component shows a simple use of React hooks.</p>
      <p>
        The useState hook is one of the most fundamental. State is distinct from
        Props.
      </p>
      <p>
        In this component I am handing the setPill function down to the
        PillChoice component to use as a callback. Have a look at the story for
        the PillChoice component and you will see how the callback can be
        anything that accepts a string.
      </p>
      <p>
        Props are handed down from parent components, whereas State is something
        that component maintains for itself.
      </p>
      <p>
        In many cases, State from one component will then be passed down as
        props to another component. For this component, the pill state is being
        passed down as props to DisplayPillChoice.
      </p>

      <PillChoice onChoice={setPill} />

      <DisplayPillChoice pill={pill} />
    </div>
  );
};

export default Step2;
