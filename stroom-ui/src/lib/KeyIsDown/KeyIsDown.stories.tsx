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
import { compose } from "recompose";
import { connect } from "react-redux";
import { storiesOf } from "@storybook/react";

import KeyIsDown from "./KeyIsDown";
import { StoreState as KeyIsDownStoreState } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";
import StroomDecorator from "../storybook/StroomDecorator";

interface Props {
  keyIsDown: KeyIsDownStoreState;
}

const TestHarness = compose<Props, {}>(
  connect<Props, {}, {}, GlobalStoreState>(({ keyIsDown }) => ({ keyIsDown })),
  KeyIsDown()
)(({ keyIsDown }: Props) => (
  <div>
    <h3>Test Keys Down/Up</h3>
    <form>
      <div>
        <label>Control</label>
        <input type="checkbox" checked={keyIsDown.Control} />
      </div>
      <div>
        <label>Cmd/Meta</label>
        <input type="checkbox" checked={keyIsDown.Meta} />
      </div>
      <div>
        <label>Shift</label>
        <input type="checkbox" checked={keyIsDown.Shift} />
      </div>
      <div>
        <label>Alt</label>
        <input type="checkbox" checked={keyIsDown.Alt} />
      </div>
    </form>
  </div>
));

storiesOf("Key Is Down", module)
  .addDecorator(StroomDecorator)
  .add("Test Component", () => <TestHarness />);
