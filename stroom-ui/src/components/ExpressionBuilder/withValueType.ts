import { withProps } from "recompose";
import {
  DataSourceFieldType,
  ExpressionTermWithUuid,
  DataSourceType
} from "../../types";

export interface Props {
  term: ExpressionTermWithUuid;
  dataSource: DataSourceType;
}

interface WithProps {
  valueType: string;
}

export interface EnhancedProps extends Props, WithProps {}

const withValueType = withProps<WithProps, Props>(({ term, dataSource }) => {
  let valueType: string = "text";

  const thisField = dataSource.fields.find(
    (f: DataSourceFieldType) => f.name === term.field
  );

  if (thisField) {
    switch (thisField.type) {
      case "FIELD":
      case "ID":
        valueType = "text";
        break;
      case "NUMERIC_FIELD":
        valueType = "number";
        break;
      case "DATE_FIELD":
        valueType = "datetime-local";
        break;
      default:
        throw new Error(`Invalid field type: ${thisField.type}`);
    }
  }

  return {
    valueType
  };
});

export default withValueType;
