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

import ElbowLine from "./ElbowLine";

import "../../../styles/main.css";

storiesOf("Pipeline/Elbow Line", module)
  .add("Pipeline Cell", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine />
    </div>
  ))
  .add("North", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine north />
    </div>
  ))
  .add("East", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine east />
    </div>
  ))
  .add("South", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine south />
    </div>
  ))
  .add("West", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine west />
    </div>
  ))
  .add("North & East", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine north east />
    </div>
  ))
  .add("North, East, South, West", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine north east south west />
    </div>
  ))
  .add("North, East, South", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine north east south />
    </div>
  ))
  .add("West, East, South", () => (
    <div className={`Pipeline-editor__elements_cell ELBOW`}>
      <ElbowLine west east south />
    </div>
  ));
