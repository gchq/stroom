/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// import { ActionTypes, PropsWithStyles, ValueType } from "react-select";
// import { DocRefType } from "../../components/DocumentEditors/useDocumentApi/types/base";
// import JsonDebug from "../JsonDebug";
// import { StroomUser, useManageUsers } from "../../components/AuthorisationManager/api/userGroups";
// import CreatableSelect from "react-select/creatable";
import * as React from "react";
import { useCallback } from "react";
// import useLocalStorage, { useStoreObjectFactory } from "../../lib/useLocalStorage";
import ThemePicker from "./ThemePicker";
import { useTheme } from "lib/useTheme/useTheme";

// const styles = {
//   topBar: {
//     display: "flex",
//     flexDirection: "row",
//     width: "100%",
//     background: "red",
//     // padding: "0.5rem",
//   },
//   themePicker: {
//     width: "10rem",
//   },
//   fullScreen: {
//     width: "100%",
//     height: "100%",
//   },
//   center: {
//     width: "100%",
//     height: "100%",
//     display: "flex",
//     justifyContent: "center",
//     alignItems: "center",
//   },
// };
//
// const themes = ["theme-light", "theme-dark"];

interface Props {
  component: any;
  centerComponent?: React.ReactNode;
}

const ThemedContainer: React.FunctionComponent<Props> = ({
  component,
  centerComponent,
}) => {
  // const storageKey = "themeValue";
  //
  // const { value, setValue } = useLocalStorage<string>(
  //   storageKey,
  //   OPTIONS[0].value,
  //   useStoreObjectFactory(),
  // );
  //

  // const { theme, setTheme } = useTheme();
  //
  // const onChange = useCallback(
  //   (option: string) => {
  //     setTheme(option);
  //   },
  //   [setTheme],
  // );

  // const onChange = React.useCallback(
  //   (d: ThemeOption) => {
  //     setTheme(d.value);
  //   },
  //   [setTheme],
  // );

  // return (
  // <div className={`${theme} raised-low ThemedStory__outer page`}>
  //   <div className="ThemedStory__top">
  //     <div>Theme:</div>
  //     <ThemePicker
  //       className="ThemedStory__themePicker"
  //       value={theme}
  //       onChange={onChange}
  //     />
  //   </div>
  //   <div className="ThemedStory__bottom">
  //     {centerComponent ? (
  //       <div className="ThemedStory__inner-center">{component()}</div>
  //     ) : (
  //       <div className="ThemedStory__inner">{component()}</div>
  //     )}
  //   </div>
  // </div>
  // );

  if (centerComponent) {
    return <div className="ThemedStory__inner-center">{component()}</div>;
  }
  return <div className="ThemedStory__inner">{component()}</div>;
};

export const addThemedStories = (
  stories: any,
  storyName: string,
  component: any,
  centerComponent?: React.ReactNode,
) => {
  stories.add(storyName, () => (
    <ThemedContainer component={component} centerComponent={centerComponent} />
  ));
};
