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

import Button from "../Button";
import IconHeader from "../IconHeader";
import reactModalOptions from "../ThemedModal/reactModalOptions";
import { GlobalStoreState } from "../../startup/reducers";

export interface Props extends ReactModal.Props {
  question: string;
  details: string;
  onConfirm: (args: any) => any;
  onCancel: (args: any) => any;
}

export interface ConnectState {
  theme: string;
}

export interface AddedProps {
  dimmer: "inverted" | true;
}

export interface EnhancedProps extends Props, ConnectState, AddedProps {}

const enhance = compose<EnhancedProps, Props>(
  connect(
    ({ userSettings: { theme } }: GlobalStoreState) => ({
      theme
    }),
    {}
  ),
  withProps(({ theme }) => ({
    dimmer: theme === "theme-light" ? "inverted" : true
  }))
);

const ThemedConfirm = ({
  dimmer,
  theme,
  question,
  details,
  onCancel,
  onConfirm,
  ...rest
}: EnhancedProps) => (
  <ReactModal className={`${theme}`} {...rest} style={reactModalOptions}>
    <div className="raised-low themed-modal">
      <header className="raised-low themed-modal__header">
        <IconHeader text={question} icon="question-circle" />
      </header>
      {details && (
        <div className="raised-low themed-modal__content">{details}</div>
      )}
      <div className="raised-low themed-modal__footer__actions">
        <Button icon="times" text="Cancel" onClick={onCancel} />
        <Button onClick={onConfirm} icon="check" text="Confirm" />
      </div>
    </div>
  </ReactModal>
);

export default enhance(ThemedConfirm);
