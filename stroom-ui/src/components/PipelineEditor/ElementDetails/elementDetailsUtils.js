const getActualValue = (value, defaultValue, type) => {
  // In case the type of the element doesn't match the type in the data.
  type = type === 'int' ? 'integer' : type;

  // If we're dealing with a boolean we need to parse the defaultValue from the string.
  if (type === 'boolean') {
    defaultValue = defaultValue === 'true';
  }

  let actualValue;

  if (value !== undefined && value.value[type] !== undefined) {
    actualValue = value.value[type];
  } else {
    actualValue = defaultValue;
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
