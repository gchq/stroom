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
import DataRetentionRuleEditor from "./DataRetentionRuleEditor";
import { DataRetentionRule } from "../types/DataRetentionRule";
import { action } from "@storybook/addon-actions";

const rule1: DataRetentionRule = {
  ruleNumber: 1,
  name: "some rule name",
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
};

interface Props {
  initialRule: DataRetentionRule;
}

const TestHarness: React.FunctionComponent<Props> = ({ initialRule }) => {
  const [rule, setRule] = React.useState<DataRetentionRule>(initialRule);
  return (
    <div>
      <DataRetentionRuleEditor
        index={1}
        value={rule}
        onChange={(rule: DataRetentionRule) => setRule(rule)}
        onDelete={action("onDelete")}
      />
      <JsonDebug value={{ rule }} />
    </div>
  );
};

storiesOf("Sections/DataRetention", module).add("Rule", () => (
  <TestHarness initialRule={rule1} />
));
