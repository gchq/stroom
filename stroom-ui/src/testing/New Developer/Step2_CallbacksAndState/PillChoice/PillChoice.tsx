import * as React from "react";

/**
 * I declare the required props here, I do not necessarily have to export this
 * interface. TypeScript will still enforce it.
 */
interface Props {
  // The type of this is a function that expects a string, and returns nothing.
  onChoice: (pill: string) => void;
}

/**
 * This component presents a couple of buttons, clicking each button will call
 * our onChoice callback with a different value.
 *
 * Notice again the use of destructuring to get the onChoice from props.
 *
 * This function uses React.Fragment, which is a way of returning multiple
 * components side by side without having to wrap them in a <div> tag.
 *
 * In this component, the onClick handlers are being created inline, later we
 * will do this differently because as it stands successive calls to render
 * (and they happen a LOT) will generate a new function each time.
 *
 * @param param0 The props
 */
export const PillChoice: React.FunctionComponent<Props> = ({ onChoice }) => {
  // Demonstrate memoization
  const setBlue = React.useCallback(() => onChoice("blue"), [onChoice]);
  const setRed = React.useCallback(() => onChoice("red"), [onChoice]);

  return (
    <React.Fragment>
      <button onClick={setBlue}>Blue</button>
      <button onClick={setRed}>Red</button>
    </React.Fragment>
  );
};

export default PillChoice;
