/**
 * Converts an expression to a string.
 * 
 * Currently the string is intended only for display, but we 
 * might want to parse it back into an expression at some point.
 */
export function toString(expression) {
  let string = '';
  if (expression.children !== undefined && expression.children.length > 0) {
    return childrenToString(expression, string)
  }
  return string;
}

function childrenToString(expression, string) {
  if (expression.children) {
    expression.children.forEach((child, i) => {
      if (child.type === 'term') {
        string += `${child.field} ${child.condition} ${child.value}`;
        if (expression.children.length > i + 1) {
          string += ` ${expression.op} `;
        }
      }
      else if (child.type === 'operator') {
        return childrenToString(child.children, string)
      }
    });
  }
  return string;
}