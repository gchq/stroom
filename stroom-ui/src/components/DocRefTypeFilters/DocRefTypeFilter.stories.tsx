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

import DocRefTypeFilters from "./DocRefTypeFilters";

import JsonDebug from "testing/JsonDebug";

const TestForm = () => {
  const [chosenTypes, setChosenTypes] = React.useState<string[]>([]);
  return (
    <form>
      <div>
        <label>Chosen Doc Types</label>
        <DocRefTypeFilters value={chosenTypes} onChange={setChosenTypes} />
      </div>

      <JsonDebug value={{ chosenTypes }} />
    </form>
  );
};

storiesOf("Doc Ref", module).add("Doc Ref Type Filters", () => <TestForm />);
