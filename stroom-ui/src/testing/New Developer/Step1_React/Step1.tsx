// This import has to be at the top of any file that uses JSX.
import * as React from "react";

// Imports must be relative
// Notice that I can import the 'directory' and it will look for the index.ts file and import stuff from there.
import CustomHeader from "./CustomHeader";

/**
 * Simple Function Component
 */
export const Step1 = () => {
  /**
   * In this function, I have declared a full function body, and included a return statement.
   * Have a look at CustomHeader for an example of simply returning the value of the function in
   * one go.
   */
  return (
    <div>
      <CustomHeader title="Hello React" />
      <p>This is a basic Function Component.</p>
      <p>
        React used to use classes, which allowed things like state and side
        effects, but that has all gone away in favour of hooks now. So Function
        Components are all you should see.
      </p>
      <p>
        This function demonstrates the composition of sub components with a
        Custom Header. This custom header requires a title prop, it then puts
        that title into a h1 tag with some fixed text.
      </p>
      <p>
        Props are a fundamental React concept, they are values which are passed
        from parent components down to children. The flow is one way in this
        regard, but props can be anything. from simple values, to callbacks, or
        other components.
      </p>
    </div>
  );
};

/**
 * If there is a function/class that is named the same as the file, then I will usually export that as a default export.
 */
export default Step1;
