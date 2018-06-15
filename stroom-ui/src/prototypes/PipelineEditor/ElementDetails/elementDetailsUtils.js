const getActualValue = (value, defaultValue, type) => {
  let actualValue;
  if (value) {
    actualValue = value[type] || defaultValue;
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
  });
  return initialValues;
};

export { getInitialValues };
