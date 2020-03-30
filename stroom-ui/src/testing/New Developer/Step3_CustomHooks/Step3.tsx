import * as React from "react";
import { useCounter } from "./useCounter";

export const Step3 = () => {
  // Here I call my custom hook, and just destructure it's parts.
  const { count, increment, decrement } = useCounter();

  return (
    <div>
      <h2>Step 3 - Custom Hooks</h2>
      <p>
        This component contains an example of a custom hook. There are some
        rules to using hooks that can be found
        <a href="https://reactjs.org/docs/hooks-rules.html">here</a>.
      </p>
      <p>
        {" "}
        Custom hooks usually wrap several calls to the standard React ones. And
        there can be a level of nesting, where some custom hooks use other ones.
        We have used this extensively in the Stroom UI
      </p>
      <p>The value of the counter is {count}</p>
      <button onClick={increment}>Increment</button>
      <button onClick={decrement}>Decrement</button>
    </div>
  );
};

export default Step3;
