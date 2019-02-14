import {
  DataSourceFieldType,
  ExpressionTermWithUuid,
  DataSourceType
} from "../../types";

const withValueType = (
  term: ExpressionTermWithUuid,
  dataSource: DataSourceType
): string => {
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

  return valueType;
};

export default withValueType;
