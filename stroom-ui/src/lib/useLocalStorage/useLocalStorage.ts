import * as React from "react";

type ValueReducer<T> = (existingValue: T) => T;

interface OutProps<T> {
  value: T;
  setValue: (newValue: T) => void;
  reduceValue: (reducer: ValueReducer<T>) => void;
  resetValue: () => void;
}

interface StringConversion<T> {
  toString: (v: T) => string;
  fromString: (v: string) => T;
}

/**
 * Pre-made utility string conversion for storing boolean
 */
export const storeBoolean: StringConversion<boolean> = {
  toString: (v) => (v ? "true" : "false"),
  fromString: (s) => s === "true",
};

/**
 * Pre-made utility string conversion for storing string (no transform)
 */
export const storeString: StringConversion<string> = {
  toString: (v) => v,
  fromString: (s) => s,
};

/**
 * Pre-made utility string conversion for storing number
 */
export const storeNumber: StringConversion<number> = {
  toString: (n) => `${n}`,
  fromString: (v) => Number.parseInt(v),
};

/**
 * Pre-made utility string conversion generator for storing objects
 */
export const useStoreObjectFactory = <T>(): StringConversion<T> => {
  return React.useMemo(
    () => ({
      toString: (v: T) => JSON.stringify(v),
      fromString: (v: string) => JSON.parse(v),
    }),
    [],
  );
};

const useLocalStorage = function <T>(
  stateName: string,
  noLocalStorageInitialState: T,
  stringConversion: StringConversion<T>,
): OutProps<T> {
  const [value, setStateValue] = React.useState<T>(noLocalStorageInitialState);

  const getFromStorage = React.useCallback(() => {
    const rawValue: string | null = localStorage.getItem(stateName);
    if (rawValue !== null) {
      return stringConversion.fromString(rawValue);
    }
    return noLocalStorageInitialState;
  }, [stateName, stringConversion, noLocalStorageInitialState]);

  React.useEffect(() => {
    const existingValue = getFromStorage();
    if (existingValue !== undefined) {
      setStateValue(existingValue);
    }
  }, [stateName, setStateValue, getFromStorage]);

  const setValue = React.useCallback(
    (valueToSet: T) => {
      const asString = stringConversion.toString(valueToSet);
      localStorage.setItem(stateName, asString);
      setStateValue(valueToSet);
    },
    [stateName, stringConversion, setStateValue],
  );

  const reduceValue = React.useCallback(
    (reducer: ValueReducer<T>) => {
      const existingValue = getFromStorage();
      const newValue = reducer(existingValue);
      setValue(newValue);
    },
    [setValue, getFromStorage],
  );

  const resetValue = React.useCallback(() => {
    setValue(noLocalStorageInitialState);
  }, [setValue, noLocalStorageInitialState]);

  return {
    value,
    setValue,
    reduceValue,
    resetValue,
  };
};

export default useLocalStorage;
