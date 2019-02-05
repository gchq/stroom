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

import IndexEditor from "./IndexEditor";

import "../../styles/main.css";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import fullTestData from "../../lib/storybook/fullTestData";

const stories = storiesOf("Document Editors/Index", module).addDecorator(
  StroomDecorator
);

let uuid: string = Object.entries(fullTestData.indexes)
  .map(k => k[0])
  .find(() => true)!;

stories.add("editor", () => <IndexEditor indexUuid={uuid} />);
