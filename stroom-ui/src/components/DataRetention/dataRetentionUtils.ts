import { DataRetentionRule } from "./types/DataRetentionRule";

const updateRuleNumbers = (rules: DataRetentionRule[]): DataRetentionRule[] => {
  const updatedRules = rules.map((rule, index) => {
    rule.ruleNumber = index + 1;
    return rule;
  });

  return updatedRules;
};

export { updateRuleNumbers };
