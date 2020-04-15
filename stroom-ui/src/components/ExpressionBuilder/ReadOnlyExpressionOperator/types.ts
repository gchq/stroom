import { ExpressionOperatorType } from "../types";

export interface Props {
  idWithinExpression?: string;
  value: ExpressionOperatorType;
  isRoot?: boolean;
  isEnabled?: boolean;
}
