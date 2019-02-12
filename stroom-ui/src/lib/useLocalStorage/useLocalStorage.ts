import { useEffect, useState } from "react";

export interface OutProps<T> {
  value: T;
  setValue: (value: T) => void;
}

export interface StringConversion<T> {
  toString: (v: T) => string;
  fromString: (v: string) => T;
}

/**
 * Pre-made utility string conversion for storing boolean
 */
export const storeBoolean: StringConversion<boolean> = {
  toString: v => (v ? "true" : "false"),
  fromString: s => s === "true"
};

/**
 * Pre-made utility string conversion for storing string (no transform)
 */
export const storeString: StringConversion<string> = {
  toString: v => v,
  fromString: s => s
};

/**
 * Pre-made utility string conversion for storing number
 */
export const storeNumber: StringConversion<number> = {
  toString: n => `${n}`,
  fromString: v => Number.parseInt(v)
};

/**
 * Pre-made utility string conversion generator for storing objects
 */
export const storeObjectFactory = <T>(): StringConversion<T> => {
  return {
    toString: v => JSON.stringify(v),
    fromString: v => JSON.parse(v)
  };
};

const useLocalStorage = function<T>(
  stateName: string,
  noLocalStorageInitialState: T,
  stringConversion: StringConversion<T>
): OutProps<T> {
  const [value, setStateValue] = useState<T>(noLocalStorageInitialState);

  useEffect(() => {
    const rawValue: string | null = localStorage.getItem(stateName);
    if (rawValue !== null) {
      const value = stringConversion.fromString(rawValue);
      setStateValue(value);
    }
  });

  const setValue = (valueToSet: T) => {
    const asString = stringConversion.toString(valueToSet);
    localStorage.setItem(stateName, asString);
    setStateValue(valueToSet);
  };

  return {
    value,
    setValue
  };
};

export default useLocalStorage;
