import { ExpressionOperator } from "../../types";

/**
 * Converts an expression to a string.
 *
 * Currently the string is intended only for display, but we
 * might want to parse it back into an expression at some point.
 */
export function toString(expression: ExpressionOperator) {
  if (expression.children !== undefined && expression.children.length > 0) {
    return childrenToString(expression);
  }
  return "";
}

function childrenToString(
  expression: ExpressionOperator,
  asString: string = ""
) {
  expression.children.forEach((child, i) => {
    if (child.enabled) {
      if (child.type === "term") {
        asString += `${child.field} ${child.condition} ${child.value}`;
        if (
          expression.children.length > i + 1 &&
          expression.children[i + 1].enabled
        ) {
          asString += ` ${expression.op} `;
        }
      } else if (child.type === "operator") {
        let childTerms = "";
        asString += `(${childrenToString(child, childTerms)})`;
      }
    }
  });
  return asString;
}
