import * as React from "react";

interface Ripple {
  id: string;
  x: number;
  y: number;
}

let nextId = 0;

interface UseRipple<T extends HTMLElement> {
  onClickWithRipple: React.MouseEventHandler<T>;
  ripples: Ripple[];
}

const MAX_RIPPLES = 5;
const reducer = (state: Ripple[], action: Ripple) => {
  if (state.length > MAX_RIPPLES) {
    return [...state, action].slice(1);
  } else {
    return [...state, action];
  }
};

interface RippleProps {
  ripples: Ripple[];
}

const RippleContainer: React.FunctionComponent<RippleProps> = ({ ripples }) => (
  <div className="ripple-container">
    {ripples.map(({ id, x, y }) => (
      <span
        key={id}
        className="ripple"
        style={{ left: `${x}px`, top: `${y}px` }}
      />
    ))}
  </div>
);

export const useRipple = <T extends HTMLElement>(
  onClick: React.MouseEventHandler<T>,
): UseRipple<T> => {
  const [ripples, dispatch] = React.useReducer(reducer, []);

  const onClickWithRipple = React.useCallback(
    (evt: React.MouseEvent<T>): void => {
      const btn = evt.currentTarget;
      const rect = btn.getBoundingClientRect();
      const x = evt.clientX - rect.left;
      const y = evt.clientY - rect.top;

      dispatch({
        id: `${nextId++}`,
        x,
        y,
      });

      if (onClick) {
        onClick(evt);
      }
    },
    [onClick, dispatch],
  );

  return {
    onClickWithRipple,
    ripples,
  };
};

export default RippleContainer;
