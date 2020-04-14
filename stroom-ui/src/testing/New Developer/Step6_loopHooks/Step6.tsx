import * as React from "react";

interface Props {
  names: string[];
  onNameClick: (name: string) => void;
}

interface NameWithOnClick {
  name: string;
  onClick: React.MouseEventHandler<HTMLElement>;
}

/**
 * Demonstrates a method by which functions that are within a loop can be memoized.
 * A new list is created of objects which encapsulate the basic value (name in this case)
 * and any custom handlers that are required.
 * This new list is then only regenerated when the list of names change.
 */
const Step6: React.FunctionComponent<Props> = ({ names, onNameClick }) => {
  const namesWithOnClicks: NameWithOnClick[] = React.useMemo(
    () =>
      names.map(name => ({
        name,
        onClick: () => onNameClick(name),
      })),
    [onNameClick, names],
  );

  return (
    <div>
      <h1>Step 6</h1>
      <ul>
        {namesWithOnClicks.map(({ name, onClick }) => (
          <button key={name} onClick={onClick}>
            {name}
          </button>
        ))}
      </ul>
    </div>
  );
};

export default Step6;
