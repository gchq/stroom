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
import { useState } from "react";
import { storiesOf } from "@storybook/react";

import Button from "../Button";
import ThemedConfirm, { useDialog } from "./ThemedConfirm";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { addThemedStories } from "../../lib/themedStoryGenerator";

import "../../styles/main.css";

let TestConfirm = () => {
  const [confirmCount, setConfirmCount] = useState<number>(0);

  const { showDialog, componentProps } = useDialog({
    question: "Are you sure about this?",
    details: "Because...nothing will really happen anyway",
    onConfirm: () => setConfirmCount(confirmCount + 1)
  });

  return (
    <React.Fragment>
      <ThemedConfirm {...componentProps} />
      <Button onClick={showDialog} text="Check" />
      <div>Number of Confirmations: {confirmCount}</div>
    </React.Fragment>
  );
};

const stories = storiesOf(
  "General Purpose/Themed Confirm",
  module
).addDecorator(StroomDecorator);

addThemedStories(stories, <TestConfirm />);
