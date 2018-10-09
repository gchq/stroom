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

import KeyIsDown from "../KeyIsDown";
import { GlobalStoreState } from "../../../startup/reducers";
import { StoreState } from "../redux";

export interface Props {}
interface ConnectState extends StoreState {}
interface ConnectDispatch {}
const enhance = compose<{}, StoreState>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ keyIsDown }) => ({ ...keyIsDown })
  ),
  KeyIsDown()
);

/**
 * This component is just here to test the KeyIsDown HOC
 */

const TestKeyIsDown = (keyIsDown: StoreState) => (
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
);

export default enhance(TestKeyIsDown);
