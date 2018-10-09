import * as React from "react";
import { ExpressionTermType } from "../../types";

export interface Props {
  term: ExpressionTermType;
  isEnabled: boolean;
  expressionId: string;
}

/**
 * Read only expression operator
 */
const ROExpressionTerm = ({ term, isEnabled, expressionId }: Props) => {
  let className = "expression-item expression-item--readonly";
  if (!isEnabled) {
    className += " expression-item--disabled";
  }
  let value = term.value;
  if (term.condition === "IN_DICTIONARY" && term.value) {
    value = term.value.name;
  }

  return (
    <div className={className}>
      {term.field} {term.condition} {value}
    </div>
  );
};

export default ROExpressionTerm;
