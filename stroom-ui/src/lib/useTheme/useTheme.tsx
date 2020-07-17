import * as React from "react";

import useLocalStorage, { storeString } from "../useLocalStorage";
import { FunctionComponent } from "react";

export interface ThemeOption {
  text: string;
  label: string;
  value: string;
}

export const themeOptions: ThemeOption[] = [
  {
    text: "Light",
    label: "Light",
    value: "theme-light",
  },
  {
    text: "Dark",
    label: "Dark",
    value: "theme-dark",
  },
];

interface ThemeContextValue {
  theme: string;
  setTheme: (t: string) => void;
}

const ThemeContext: React.Context<ThemeContextValue> = React.createContext({
  theme: themeOptions[0].value,
  setTheme: (t: string) =>
    console.log("Theme Change Ignored, something wrong with context setup", {
      t,
    }),
});

const ThemeContextProvider: FunctionComponent = ({ children }) => {
  const { value, setValue: setTheme } = useLocalStorage(
    "theme",
    themeOptions[0].value,
    storeString,
  );

  return (
    <ThemeContext.Provider value={{ theme: value, setTheme }}>
      <div className={value}>{children}</div>
    </ThemeContext.Provider>
  );
};

const useTheme = (): ThemeContextValue => {
  return React.useContext(ThemeContext);
};

export { ThemeContext, ThemeContextProvider, useTheme };

export default useTheme;
