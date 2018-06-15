const getActualValue = (value, defaultValue, type) => {
  // In case the type of the element doesn't not macth the type in the data.
  type = type === 'int' ? 'integer' : type;
  let actualValue;
  if (value) {
    actualValue = value.value[type] || defaultValue;
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
