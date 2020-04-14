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

import { useDataRetentionState } from "./useDataRetentionState";
import { useApi } from "./useApi";
import { useCallback } from "react";
import { DataRetentionPolicy } from "./types/DataRetentionPolicy";
import { DataRetentionRule } from "./types/DataRetentionRule";
import { updateRuleNumbers } from "./dataRetentionUtils";

const useDataRetention = () => {
  const { policy, setPolicy } = useDataRetentionState();

  const { fetchPolicy: fetchPolicyApi } = useApi();
  const fetchPolicy = useCallback(() => {
    fetchPolicyApi().then((policy: DataRetentionPolicy) => {
      setPolicy(policy);
    });
  }, [fetchPolicyApi, setPolicy]);

  /**
   * Create adds a rule to the policy and sends an update to the API.
   * I.e. there is no actual creation of a new entity, because all
   * rules are actually part of the singleton policy.
   */
  const create = useCallback((dataRetentionPolicy: DataRetentionPolicy) => {
    // When a user creates a new rule it will look like this:
    //   1. it's disabled
    //   2. it keeps everything forever
    const newRuleTemplate: DataRetentionRule = {
      ruleNumber: 2,
      name: "New rule",
      enabled: false,
      age: 1,
      timeUnit: "Years",
      forever: true,
      expression: {
        op: "AND",
        children: [],
        enabled: true,
        type: "operator",
      },
    };

    // We'll put it at the front of the rules array and then re-order the whole
    // thing, before doing an update.
    dataRetentionPolicy.rules.unshift(newRuleTemplate);
    dataRetentionPolicy.rules = updateRuleNumbers(dataRetentionPolicy.rules);

    //TODO call update
    //TODO: write tests for this
  }, []);

  return { policy, fetchPolicy, create };
};

export default useDataRetention;
