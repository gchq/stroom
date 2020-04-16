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

import useKeyIsDown from "./useKeyIsDown";

import { KeyDownState } from "./types";

interface Props {
  filters?: string[];
}

const TestHarness: React.FunctionComponent<Props> = ({ filters }) => {
  const keyIsDown: KeyDownState = useKeyIsDown(filters);
  return (
    <div>
      <h3>Test Keys Down/Up</h3>

      <form>
        {Object.entries(keyIsDown)
          .map(k => ({ key: k[0], isDown: k[1] }))
          .map(({ key, isDown }) => (
            <div key={key}>
              <label>{key}</label>
              <input type="checkbox" readOnly checked={isDown} />
            </div>
          ))}
      </form>
    </div>
  );
};

storiesOf("lib/useKeyIsDown", module)
  .add("default", () => <TestHarness />)
  .add("control/alt", () => <TestHarness filters={["Control", "Alt"]} />)
  .add("up/down/left/right", () => (
    <TestHarness
      filters={["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"]}
    />
  ));
