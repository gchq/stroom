import * as React from "react";

import SingleValueWidget from "./SingleValueWidget";
import BetweenValueWidget from "./BetweenValueWidget";
import InValueWidget from "./InValueWidget";
import { ExpressionTermType } from "../types";
import AppSearchBarWidget from "./AppSearchBarWidget";
import { useCallback } from "react";

interface Props {
  onChange: (value: any) => any;
  term: ExpressionTermType;
  valueType: string;
}

const ValueWidget: React.FunctionComponent<Props> = ({
  term: { value, condition },
  onChange,
  valueType,
}) => {
  const handleChange = useCallback((event) => onChange(event.target.value), [
    onChange,
  ]);
  switch (condition) {
    case "CONTAINS":
    case "EQUALS":
    case "GREATER_THAN":
    case "GREATER_THAN_OR_EQUAL_TO":
    case "LESS_THAN":
    case "LESS_THAN_OR_EQUAL_TO": {
      return (
        <SingleValueWidget
          value={value}
          valueType={valueType}
          onChange={handleChange}
        />
      );
    }
    case "BETWEEN": {
      return (
        <BetweenValueWidget
          value={value}
          valueType={valueType}
          onChange={onChange}
        />
      );
    }
    case "IN": {
      return <InValueWidget value={value} onChange={onChange} />;
    }
    case "IN_DICTIONARY": {
      return <AppSearchBarWidget value={value} onChange={onChange} />;
    }
    default:
      throw new Error(`Invalid condition: ${condition}`);
  }
};

export default ValueWidget;
