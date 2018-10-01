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
import { compose, withProps } from "recompose";
import { connect } from "react-redux";
import * as ReactModal from "react-modal";

import { GlobalStoreState } from "../../startup/reducers";
import reactModalOptions from "./reactModalOptions";

export interface Props extends ReactModal.Props {
  header: JSX.Element;
  content: JSX.Element;
  actions: JSX.Element;
}

interface ConnectState {
  theme: string;
}
interface ConnectDispatch {}

interface WithProps {
  dimmer: string | boolean;
}

export interface EnhancedProps extends Props, ConnectState, WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userSettings: { theme } }) => ({
      theme
    }),
    {}
  ),
  withProps(({ theme }) => ({
    dimmer: theme === "theme-light" ? "inverted" : true
  }))
);

/**
 * A themed modal is required because Semantic UI modals are mounted
 * outside the application's root div. This means it won't inherit the
 * 'theme-dark' or 'theme-light' class name. We can add it here easily
 * enough, and it gives us the opportunity to set the SUI dimmer
 * property, or not.
 */
const ThemedModal = ({
  dimmer,
  theme,
  header,
  content,
  actions,
  ...rest
}: EnhancedProps) => (
  <ReactModal className={`${theme}`} {...rest} style={reactModalOptions}>
    <div className="raised-low themed-modal">
      <header className="raised-low themed-modal__header">{header}</header>
      <div className="raised-low themed-modal__content">{content}</div>
      <div className="raised-low themed-modal__footer__actions">{actions}</div>
    </div>
  </ReactModal>
);

export default enhance(ThemedModal);
