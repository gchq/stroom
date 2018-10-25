import * as React from "react";

import ROExpressionTerm from "./ROExpressionTerm";

import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import {
  ExpressionOperatorWithUuid,
  ExpressionItemWithUuid,
  ExpressionTermWithUuid
} from "../../types";

export interface Props {
  expressionId: string;
  operator: ExpressionOperatorWithUuid;
  isRoot?: boolean;
  isEnabled: boolean;
}

/**
 * Read only expression operator
 */
const ROExpressionOperator = ({
  expressionId,
  operator,
  isRoot,
  isEnabled
}: Props) => {
  let className = "expression-item expression-item--readonly";
  if (isRoot) {
    className += " expression-item__root";
  }
  if (!isEnabled) {
    className += " expression-item--disabled";
  }

  return (
    <div className={className}>
      <div>
        <span>
          <FontAwesomeIcon icon="circle" />
        </span>
        <span>{operator.op}</span>
      </div>
      <div className="operator__children">
        {operator.children &&
          operator.children
            .map((c: ExpressionItemWithUuid) => {
              let itemElement;
              const cIsEnabled = isEnabled && c.enabled;
              switch (c.type) {
                case "term":
                  itemElement = (
                    <div key={c.uuid}>
                      <ROExpressionTerm
                        expressionId={expressionId}
                        isEnabled={cIsEnabled}
                        term={c as ExpressionTermWithUuid}
                      />
                    </div>
                  );
                  break;
                case "operator":
                  itemElement = (
                    <ROExpressionOperator
                      expressionId={expressionId}
                      isEnabled={cIsEnabled}
                      operator={c as ExpressionOperatorWithUuid}
                    />
                  );
                  break;
                default:
                  throw new Error(`Invalid operator type: ${c.type}`);
              }

              // Wrap it with a line to
              return <div key={c.uuid}>{itemElement}</div>;
            })
            .filter(c => !!c) // null filter
        }
      </div>
    </div>
  );
};

export default ROExpressionOperator;
