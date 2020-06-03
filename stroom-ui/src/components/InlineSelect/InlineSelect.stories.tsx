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
import { ChangeEventHandler, useCallback, useState } from "react";
import JsonDebug from "testing/JsonDebug";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import InlineSelect, { SelectOption } from "./InlineSelect";

const stories = storiesOf("General Purpose", module);

const options: SelectOption[] = [
  { value: "leia", label: "Princess Leia" },
  { value: "han", label: "Han Solo" },
  { value: "jabba", label: "Jabba the Hut" },
  { value: "luke", label: "Luke Skywalker" },
  { value: "everyone", label: "everyone" },
];

addThemedStories(stories, "InlineSelect", () => {
  const [empty, setEmpty] = useState<string>("");
  const onEmptyChangeHandler: ChangeEventHandler<
    HTMLSelectElement
  > = useCallback(({ target: { value } }) => setEmpty(value), [setEmpty]);

  const [single, setSingle] = useState<string>("han");
  const onSingleChangeHandler: ChangeEventHandler<
    HTMLSelectElement
  > = useCallback(({ target: { value } }) => setSingle(value), [setSingle]);

  const [placeholder, setPlaceholder] = useState<string>("leia");
  const onPlaceholderChangeHandler: ChangeEventHandler<
    HTMLSelectElement
  > = useCallback(({ target: { value } }) => setPlaceholder(value), [
    setPlaceholder,
  ]);
  return (
    <div style={{ padding: "5em" }}>
      <h1>InlineSelect</h1>
      <p>
        An edit-in-place <code>select</code>, to be used inline with text.
      </p>
      <form>
        <h2>Simplest</h2>
        <span>I would like to feed </span>
        <InlineSelect
          options={options}
          selected={empty}
          onChange={onEmptyChangeHandler}
        />
        <span> to the sarlacc.</span>

        <h2>With an existing value</h2>
        <span>I would like to feed </span>
        <InlineSelect
          options={options}
          selected={single}
          onChange={onSingleChangeHandler}
        />
        <span> to the sarlacc.</span>

        <h2>With a placeholder</h2>
        <span>I would like to feed </span>
        <InlineSelect
          options={options}
          placeholder="+"
          selected={placeholder}
          onChange={onPlaceholderChangeHandler}
        />
        <span> to the sarlacc.</span>
      </form>
      <JsonDebug value={{ empty, single, placeholder }} />
    </div>
  );
});
