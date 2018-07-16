const getActualValue = (value, type) => {
  // In case the type of the element doesn't match the type in the data.
  type = type === 'int' ? 'integer' : type;

  let actualValue;

  if (value !== undefined && value.value[type] !== undefined) {
    actualValue = value.value[type];
  } else {
    actualValue = undefined;
  }

  return actualValue;
};

const getInitialValues = (elementTypeProperties, elementProperties) => {
  const initialValues = {};
  Object.keys(elementTypeProperties).map((key) => {
    const elementsProperty = elementProperties.find(element => element.name === key);
    initialValues[key] = getActualValue(
      elementsProperty,
      elementTypeProperties[key].defaultValue,
      elementTypeProperties[key].type,
    );
    return null;
  });
  return initialValues;
};

export { getActualValue, getInitialValues };
