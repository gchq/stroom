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

import DocRefTypePicker from "./DocRefTypePicker";

import JsonDebug from "testing/JsonDebug";

const invalidTypes: string[] = ["Folder", "Dictionary", "XSLT"];

const TestForm = () => {
  const [docRefType, setDocRefType] = React.useState<string>("");

  return (
    <form>
      <div>
        <label>Chosen Doc Type</label>
        <DocRefTypePicker
          value={docRefType}
          onChange={setDocRefType}
          invalidTypes={invalidTypes}
        />
      </div>

      <JsonDebug value={{ invalidTypes, docRefType }} />
    </form>
  );
};

storiesOf("Doc Ref", module).add("Doc Ref Type Picker", () => <TestForm />);
