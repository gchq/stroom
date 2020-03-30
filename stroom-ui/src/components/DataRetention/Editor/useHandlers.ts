import { DataRetentionRule } from "../types/DataRetentionRule";
import { ChangeEventHandler, useCallback } from "react";

const useHandlers = (
  rule: DataRetentionRule,
  onChange: (dataRetentionRule: DataRetentionRule) => void,
) => {
  const handleNameChange: ChangeEventHandler<HTMLInputElement> = useCallback(
    e => onChange({ ...rule, name: e.target.value }),
    [rule, onChange],
  );

  const handleAgeChange: ChangeEventHandler<HTMLInputElement> = useCallback(
    e => onChange({ ...rule, age: Number(e.target.value) }),
    [rule, onChange],
  );

  const handleExpressionChange = useCallback(
    expression => onChange({ ...rule, expression }),
    [rule, onChange],
  );

  const handleTimeUnitChange = useCallback(
    e => onChange({ ...rule, timeUnit: e.target.value }),
    [rule, onChange],
  );

  const handleEnabledChange = useCallback(
    e => onChange({ ...rule, enabled: e }),
    [rule, onChange],
  );

  const handleForeverChange = useCallback(
    e => onChange({ ...rule, forever: e.target.value === "keep_forever" }),
    [rule, onChange],
  );

  return {
    handleNameChange,
    handleAgeChange,
    handleExpressionChange,
    handleTimeUnitChange,
    handleEnabledChange,
    handleForeverChange,
  };
};
export default useHandlers;
