import { withProps } from 'recompose';

const withValueType = withProps(({term, dataSource}) => {
  let valueType = 'text';

  const thisField = dataSource.fields.find(f => f.name === term.field);

  if (thisField) {
    switch (thisField.type) {
      case 'FIELD':
      case 'ID':
        valueType = 'text';
        break;
      case 'NUMERIC_FIELD':
        valueType = 'number';
        break;
      case 'DATE_FIELD':
        valueType = 'datetime-local';
        break;
      default:
        throw new Error(`Invalid field type: ${thisField.type}`);
    }
  }

  return {
    valueType
  }
});

export default withValueType;