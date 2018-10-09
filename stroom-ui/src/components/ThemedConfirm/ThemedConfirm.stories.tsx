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
import { storiesOf } from "@storybook/react";
import { compose, withState } from "recompose";

import Button from "../Button";
import ThemedConfirm from "./ThemedConfirm";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { addThemedStories } from "../../lib/themedStoryGenerator";

import "../../styles/main.css";

interface WithModalOpen {
  modalIsOpen: boolean;
  setModalIsOpen: (v: boolean) => any;
}
interface WithWasConfirmed {
  isConfirmed: ConfirmState;
  setIsConfirmed: (v: ConfirmState) => any;
}

interface Props {}
interface EnhancedProps extends Props, WithModalOpen, WithWasConfirmed {}

const withModalOpen = withState("modalIsOpen", "setModalIsOpen", false);

enum ConfirmState {
  UNUSED = "unused",
  CONFIRMED = "confirmed",
  CANCELLED = "cancelled"
}
const withWasConfirmed = withState(
  "isConfirmed",
  "setIsConfirmed",
  ConfirmState.UNUSED
);

const enhance = compose<EnhancedProps, Props>(
  withModalOpen,
  withWasConfirmed
);

let TestConfirm = enhance(
  ({
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
  )
);

const stories = storiesOf("Themed Confirm", module).addDecorator(
  StroomDecorator
);

addThemedStories(stories, <TestConfirm />);
