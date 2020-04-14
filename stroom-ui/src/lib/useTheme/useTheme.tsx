import * as React from "react";

import useLocalStorage, { storeString } from "../useLocalStorage";

export interface ThemeOption {
  text: string;
  value: string;
}

export const themeOptions: ThemeOption[] = [
  {
    text: "Light",
    value: "theme-light",
  },
  {
    text: "Dark",
    value: "theme-dark",
  },
];

interface ThemeContextValue {
  theme: string;
  setTheme: (t: string) => void;
}

let ThemeContext: React.Context<ThemeContextValue> = React.createContext({
  theme: themeOptions[0].value,
  setTheme: (t: string) =>
    console.log("Theme Change Ignored, something wrong with context setup", {
      t,
    }),
});

const ThemeContextProvider: React.StatelessComponent<{}> = ({ children }) => {
  const { value, setValue: setTheme } = useLocalStorage(
    "theme",
    themeOptions[0].value,
    storeString,
  );

  return (
    <ThemeContext.Provider value={{ theme: value, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

const useTheme = (): ThemeContextValue => {
  return React.useContext(ThemeContext);
};

export { ThemeContext, ThemeContextProvider, useTheme };

export default useTheme;
