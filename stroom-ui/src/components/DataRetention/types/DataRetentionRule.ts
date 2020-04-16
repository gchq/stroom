import { ExpressionOperatorType } from "components/ExpressionBuilder/types";

export interface DataRetentionRule {
  name: string;
  ruleNumber: number;
  enabled: boolean;
  age: number;
  forever: boolean;
  timeUnit: "Minutes" | "Hours" | "Days" | "Weeks" | "Months" | "Years";
  expression: ExpressionOperatorType;
}
