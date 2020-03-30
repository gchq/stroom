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
import * as React from "react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import InlineMultiInput from "./InlineMultiInput";
import JsonDebug from "testing/JsonDebug";

const stories = storiesOf("General Purpose/InlineMultiInput", module);

addThemedStories(stories, () => {
  const [empty, setEmpty] = React.useState<string[]>([]);
  const [single, setSingle] = React.useState<string[]>(["Leia"]);
  const [multi, setMulti] = React.useState<string[]>([
    "Han Solo",
    "Jabba The Hut",
    "C3PO",
  ]);

  return (
    <div style={{ padding: "5em" }}>
      <h1>InlineMultiInput</h1>
      <p>
        An edit-in-place multi<code>input</code>, to be used inline with text.
        Allows adding multiple values.
      </p>
      <form>
        <h2>Empty</h2>
        <span>I would like to feed </span>
        <InlineMultiInput value={empty} onChange={setEmpty} />
        <span> to the sarlacc.</span>

        <h2>Empty</h2>
        <span>I would like to feed </span>
        <InlineMultiInput value={single} onChange={setSingle} />
        <span> to the sarlacc.</span>

        <h2>With some default values</h2>
        <span>I would like to feed </span>
        <InlineMultiInput value={multi} onChange={setMulti} />
        <span> to the sarlacc.</span>
      </form>
      <JsonDebug value={{ empty, single, multi }} />
    </div>
  );
});
