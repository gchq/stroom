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

import * as React from "react";
import { connect } from "react-redux";
import { compose, withHandlers } from "recompose";

import IconHeader from "../../components/IconHeader";
import { actionCreators, StoreAction } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";
import { ActionCreator } from "redux";

const { themeChanged } = actionCreators;

const themeOptions = [
  {
    text: "Light",
    value: "theme-light"
  },
  {
    text: "Dark",
    value: "theme-dark"
  }
];

export interface Props {}

export interface ConnectProps {
  theme: string;
}

export interface ConnectActions {
  themeChanged: ActionCreator<StoreAction>;
}

export interface Handlers {
  onThemeChanged: (event: any) => void; // React.ChangeEventHandler
}

export interface EnhancedProps
  extends Props,
    ConnectProps,
    ConnectActions,
    Handlers {}

const enhance = compose<EnhancedProps, Props>(
  connect(
    ({ userSettings: { theme } }: GlobalStoreState) => ({
      theme
    }),
    { themeChanged }
  ),
  withHandlers<Props & ConnectProps & ConnectActions, Handlers>({
    onThemeChanged: ({ themeChanged }) => event => {
      themeChanged(event.target.value);
    }
  })
);

const UserSettings = ({ theme, onThemeChanged }: EnhancedProps) => (
  <React.Fragment>
    <IconHeader text="Me" icon="user" />
    <div className="UserSettings__container">
      <h3>User Settings</h3>
      <div>
        <label>Theme:</label>
        <select onChange={onThemeChanged} value={theme}>
          {themeOptions.map(theme => (
            <option key={theme.value} value={theme.value}>
              {theme.text}
            </option>
          ))}
        </select>
      </div>
    </div>
  </React.Fragment>
);

export default enhance(UserSettings);
