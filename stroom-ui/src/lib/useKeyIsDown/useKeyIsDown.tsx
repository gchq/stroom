import { useEffect, useState } from "react";

export interface KeyDownState {
  [s: string]: boolean;
}

export const DEFAULT_FILTERS = ["Control", "Shift", "Alt", "Meta"];

const useKeyIsDown = function(
  filters: Array<string> = DEFAULT_FILTERS
): KeyDownState {
  const [keysDown, setKeysDown] = useState<KeyDownState>(
    filters.reduce((acc, c) => ({ ...acc, [c]: false }), {})
  );

  useEffect(() => {
    const onkeydown = (e: KeyboardEvent) => {
      if (filters.indexOf(e.key) !== -1) {
        setKeysDown({ ...keysDown, [e.key]: true });
        e.preventDefault();
      }
    };
    document.addEventListener("keydown", onkeydown);

    const onkeyup = (e: KeyboardEvent) => {
      if (filters.indexOf(e.key) !== -1) {
        setKeysDown({ ...keysDown, [e.key]: false });
        e.preventDefault();
      }
    };
    document.addEventListener("keyup", onkeyup);

    return () => {
      document.removeEventListener("keydown", onkeydown);
      document.removeEventListener("keyup", onkeyup);
    };
  }, []); // empty array prevents this code re-running for state changes

  return keysDown;
};

export default useKeyIsDown;
