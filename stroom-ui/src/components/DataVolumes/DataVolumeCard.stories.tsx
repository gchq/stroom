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
import FsVolume from "./types/FsVolume";
import DataVolumeCard from "./DataVolumeCard";

const volume01: FsVolume = {
  id: "123",
  version: 1,
  path: "/some/amazing/path",
  status: "ACTIVE",
  byteLimit: 1000,
  volumeState: {
    id: 1,
    version: 1,
    bytesUsed: 20423423,
    bytesFree: 345345,
    bytesTotal: 23423423,
    updateTimeMs: 2342,
  },
  updateTimeMs: Date.now(),
  updateUser: "Updating user",
  createTimeMs: 234534345,
  createUser: "Creating user",
};

storiesOf("Sections/Data Volumes", module).add("DataVolumeCard", () => (
  <div style={{ padding: "1em" }}>
    <DataVolumeCard
      volume={volume01}
      onDelete={() => console.log("onDelete")}
      onChange={() => console.log("onChange")}
    />
  </div>
));
