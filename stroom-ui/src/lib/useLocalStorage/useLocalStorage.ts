import { useEffect, useState } from "react";

export interface OutProps {
  value: any;
  setValue: (value: any) => void;
}

const useLocalStorage = function(
  stateName: string,
  noLocalStorageInitialState: any
): OutProps {
  const [value, setStateValue] = useState(noLocalStorageInitialState);

  useEffect(() => {
    const rawValue = localStorage.getItem(stateName);
    if (rawValue) {
      // localStorage uses strings, so we need to make sure that if we've
      // stored a boolean it gets correctly converted back to one.
      setStateValue(
        rawValue === "true" ? true : rawValue === "false" ? false : rawValue
      );
    }
  });

  const setValue = (valueToSet: any) => {
    localStorage.setItem(stateName, valueToSet);
    setStateValue(valueToSet);
  };

  return {
    value,
    setValue
  };
};

export default useLocalStorage;
