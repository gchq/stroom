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
import JsonDebug from "testing/JsonDebug";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import { DataRetentionRule } from "../types/DataRetentionRule";
import DataRetentionRuleList from "./DataRetentionRuleList";
import { useCallback } from "react";

const stories = storiesOf("Sections/DataRetention", module);

// These are out of order in this list because they should be
// correctly ordered by ruleNumber the component
const rules1: DataRetentionRule[] = [
  {
    ruleNumber: 0,
    name: "First rule",
    enabled: true,
    age: 1,
    timeUnit: "Years",
    forever: false,
    expression: {
      op: "AND",
      children: [],
      enabled: true,
      type: "operator",
    },
  },
  {
    ruleNumber: 2,
    name: "Third rule",
    enabled: true,
    age: 1,
    timeUnit: "Weeks",
    forever: false,
    expression: {
      op: "AND",
      children: [],
      enabled: true,
      type: "operator",
    },
  },
  {
    ruleNumber: 1,
    name: "Second rule",
    enabled: true,
    age: 2,
    timeUnit: "Months",
    forever: false,
    expression: {
      op: "AND",
      children: [],
      enabled: true,
      type: "operator",
    },
  },
  {
    ruleNumber: 3,
    name: "Forth rule",
    enabled: true,
    age: 1,
    timeUnit: "Weeks",
    forever: false,
    expression: {
      op: "AND",
      children: [],
      enabled: true,
      type: "operator",
    },
  },
  {
    ruleNumber: 4,
    name: "Fifth rule",
    enabled: true,
    age: 1,
    timeUnit: "Weeks",
    forever: false,
    expression: {
      op: "AND",
      children: [],
      enabled: true,
      type: "operator",
    },
  },
];

addThemedStories(stories, "List", () => {
  const [rules, setRules] = React.useState<DataRetentionRule[]>(rules1);
  const onRulesChange = useCallback(rules => setRules(rules), [setRules]);
  return (
    <div>
      <DataRetentionRuleList value={rules} onChange={onRulesChange} />
      <JsonDebug value={{ rules }} />
    </div>
  );
});
