/*
 * Copyright 2019 Crown Copyright
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

import Button from "components/Button";
import IconHeader from "components/IconHeader";
import * as React from "react";
import DataRetentionRuleList from "../List/DataRetentionRuleList";
import { DataRetentionPolicy } from "../types/DataRetentionPolicy";
import { DataRetentionRule } from "../types/DataRetentionRule";

interface Props {
  policy: DataRetentionPolicy;
  onCreate: () => void;
  onUpdate: (dataRetentionPolicy: DataRetentionPolicy) => void;
}

const DataRetentionSection: React.FunctionComponent<Props> = ({
  policy,
  onCreate,
  onUpdate,
}) => {
  const handleUpdate = React.useCallback(
    (rules: DataRetentionRule[]) => {
      policy.rules = rules;
      onUpdate(policy);
    },
    [onUpdate, policy],
  );

  return (
    <div className="page">
      <div className="page__header">
        <IconHeader text="Data Retention Policy" icon="trash-alt" />
        <div className="page__buttons Button__container">
          <Button onClick={onCreate} icon="plus" text="Create" />
        </div>
      </div>
      <div className="page__search" />
      <div className="DataRetentionSection__content">
        <DataRetentionRuleList value={policy.rules} onChange={handleUpdate} />
      </div>
    </div>
  );
};

export default DataRetentionSection;
