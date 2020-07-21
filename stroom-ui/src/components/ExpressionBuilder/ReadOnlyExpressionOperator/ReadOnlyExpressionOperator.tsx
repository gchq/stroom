import * as React from "react";

import ReadOnlyExpressionTerm from "../ReadOnlyExpressionTerm";

import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import {
  ExpressionOperatorType,
  ExpressionTermType,
  ExpressionItem,
} from "../types";
import { LineEndpoint, LineTo } from "components/LineTo";
import { Props } from "./types";

/**
 * Read only expression operator
 */
const ReadOnlyExpressionOperator: React.FunctionComponent<Props> = ({
  idWithinExpression = "root",
  value,
  isRoot,
  isEnabled = true,
}) => {
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
        <LineEndpoint
          className="ExpressionOperator__circle"
          lineEndpointId={idWithinExpression}
        >
          <FontAwesomeIcon icon="circle" />
        </LineEndpoint>
        <span>{value.op}</span>
      </div>
      <div className="operator__children">
        {
          value.children &&
            value.children
              .map((c: ExpressionItem, i) => {
                let itemElement;
                const itemLineEndpointId = `${idWithinExpression}-${i}`;
                const cIsEnabled = isEnabled && c.enabled;
                switch (c.type) {
                  case "term":
                    itemElement = (
                      <div key={i}>
                        <ReadOnlyExpressionTerm
                          idWithinExpression={itemLineEndpointId}
                          isEnabled={cIsEnabled}
                          value={c as ExpressionTermType}
                        />
                      </div>
                    );
                    break;
                  case "operator":
                    itemElement = (
                      <ReadOnlyExpressionOperator
                        idWithinExpression={itemLineEndpointId}
                        isEnabled={cIsEnabled}
                        value={c as ExpressionOperatorType}
                      />
                    );
                    break;
                  default:
                    throw new Error(`Invalid operator type: ${c.type}`);
                }

                // Wrap it with a line to
                return (
                  <React.Fragment key={i}>
                    {itemElement}
                    <LineTo
                      fromId={idWithinExpression}
                      toId={itemLineEndpointId}
                    />
                  </React.Fragment>
                );
              })
              .filter((c) => !!c) // null filter
        }
      </div>
    </div>
  );
};

export default ReadOnlyExpressionOperator;
