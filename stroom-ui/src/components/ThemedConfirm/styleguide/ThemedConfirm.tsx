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
import { compose, withStateHandlers } from "recompose";

import Button from "../../Button";
import ThemedConfirm from "../";

enum ConfirmState {
  UNUSED = "unused",
  CONFIRMED = "confirmed",
  CANCELLED = "cancelled"
}

export interface StateProps {
  modalIsOpen: boolean;
  isConfirmed: ConfirmState;
}

export interface StateHandlers {
  setModalIsOpen: (a: boolean) => void;
  setIsConfirmed: (a: ConfirmState) => void;
}

export interface EnhancedProps extends StateProps, StateHandlers {}

const enhance = compose<EnhancedProps, {}>(
  withStateHandlers(
    ({
      modalIsOpen = false,
      isConfirmed = ConfirmState.UNUSED
    }: StateProps) => ({
      modalIsOpen,
      isConfirmed
    }),
    {
      setModalIsOpen: (state: StateProps) => (modalIsOpen: boolean) => ({
        ...state,
        modalIsOpen
      }),
      setIsConfirmed: (state: StateProps) => (isConfirmed: ConfirmState) => ({
        ...state,
        isConfirmed
      })
    }
  )
);

let TestConfirm = ({
  modalIsOpen,
  setModalIsOpen,
  isConfirmed,
  setIsConfirmed
}: EnhancedProps) => (
  <React.Fragment>
    <ThemedConfirm
      isOpen={modalIsOpen}
      question="Are you sure about this?"
      details="Because...nothing will really happen anyway"
      onConfirm={() => {
        setIsConfirmed(ConfirmState.CONFIRMED);
        setModalIsOpen(false);
      }}
      onCancel={() => {
        setIsConfirmed(ConfirmState.CANCELLED);
        setModalIsOpen(false);
      }}
      onRequestClose={() => setModalIsOpen(false)}
    />
    <Button onClick={() => setModalIsOpen(!modalIsOpen)} text="Check" />
    <div>Current State: {isConfirmed}</div>
  </React.Fragment>
);

export default enhance(TestConfirm);
