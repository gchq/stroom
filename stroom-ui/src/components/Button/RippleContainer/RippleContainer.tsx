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
    (e: React.MouseEvent<T>) => {
      const btn = e.currentTarget;
      const rect = btn.getBoundingClientRect();

      let x = rect.width / 2;
      let y = rect.height / 2;
      if (e.clientX > 0 || e.clientY > 0) {
        x = e.clientX - rect.left;
        y = e.clientY - rect.top;
      }

      dispatch({
        id: `${nextId++}`,
        x,
        y,
      });

      if (onClick) {
        onClick(e);
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
