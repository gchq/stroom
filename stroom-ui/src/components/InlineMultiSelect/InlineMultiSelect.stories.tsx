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

import { storiesOf } from "@storybook/react";
import { SelectOption } from "components/InlineSelect/InlineSelect";
import * as React from "react";
import { useState } from "react";
import JsonDebug from "testing/JsonDebug";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import InlineMultiSelect from "./InlineMultiSelect";

const stories = storiesOf("General Purpose", module);

const options: SelectOption[] = [
  { value: "leia", label: "Princess Leia" },
  { value: "han", label: "Han Solo" },
  { value: "jabba", label: "Jabba the Hut" },
  { value: "luke", label: "Luke Skywalker" },
  { value: "everyone", label: "everyone" },
];

addThemedStories(stories, "InlineMultiSelect", () => {
  const [emptySelection, setEmptySelection] = useState<string[]>([]);
  const [singleSelection, setSingleSelection] = useState<string[]>(["leia"]);
  const [multiSelection, setMultiSelection] = useState<string[]>([
    "leia",
    "han",
    "luke",
  ]);
  return (
    <div style={{ padding: "5em" }}>
      <h1>InlineMultiSelect</h1>
      <p>
        An edit-in-place multi<code>select</code>, to be used inline with text.
        Allows the selection of multiple options.
      </p>
      <form>
        <h2>Empty</h2>
        <span>I would like to feed </span>
        <InlineMultiSelect
          options={options}
          selected={emptySelection}
          onChange={setEmptySelection}
        />
        <span> to the sarlacc.</span>

        <h2>Single selection</h2>
        <span>I would like to feed </span>
        <InlineMultiSelect
          options={options}
          selected={singleSelection}
          onChange={setSingleSelection}
        />
        <span> to the sarlacc.</span>

        <h2>Multiple selection</h2>
        <span>I would like to feed </span>
        <InlineMultiSelect
          options={options}
          selected={multiSelection}
          onChange={setMultiSelection}
        />
        <span> to the sarlacc.</span>
      </form>
      <JsonDebug value={{ emptySelection, singleSelection, multiSelection }} />
    </div>
  );
});
