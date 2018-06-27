import React from 'react';
import PropTypes from 'prop-types';

import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { Form, Popup, Icon } from 'semantic-ui-react';

import { Toggle, InputField } from 'react-semantic-redux-form';

import { Field } from 'redux-form';

import { getActualValue } from './elementDetailsUtils';

import { DocRefModalPicker, actionCreators } from 'components/DocExplorer';

import NumericInput from 'prototypes/NumericInput';

const { docRefPicked } = actionCreators;

const camelize = str => str.replace(/\W+(.)/g, (match, chr) => chr.toUpperCase());

const getPickerName = (elementName, settingName) =>
  `${camelize(elementName)}_${settingName}_docRefModalPicker`;

const enhance = compose(
  connect(
    (state, props) => ({
      // state
    }),
    {
      docRefPicked,
    },
  ),
  lifecycle({
    componentDidMount() {
      this.props.docRefPicked(
        getPickerName(this.props.value.element, this.props.name),
        this.props.value.value.entity,
      );
    },
  }),
);

const ElementFieldType = ({
  name, type, value, defaultValue, docRefTypes,
}) => {
  let actualValue;
  switch (type) {
    case 'boolean':
      actualValue = getActualValue(value, defaultValue, 'boolean') === 'true';
      return <Field name={name} component={Toggle} value={actualValue} checked={actualValue} />;
    case 'int':
      actualValue = parseInt(getActualValue(value, defaultValue, 'integer'), 10);
      return (
        <Field
          name={name}
          value={actualValue}
          component={props => <NumericInput {...props.input} />}
        />
      );
    case 'DocRef':
      // TODO potential bug: I'm not sure why elementTypeProperties have multiple
      // docRefTypes, but we can only use one so we'll choose the first.
      return (
        <DocRefModalPicker
          pickerId={getPickerName(value.element, name)}
          typeFilter={docRefTypes[0]}
        />
      );
    case 'String':
    case 'PipelineReference':
      actualValue = getActualValue(value, defaultValue, 'string');
      return <Field name={name} component={InputField} value={actualValue} />;
    default:
      actualValue = getActualValue(value, defaultValue, 'string');
      return <Field name={name} component={InputField} value={actualValue} />;
  }
};

const ElementField = enhance(({
  name, description, type, defaultValue, value, docRefTypes,
}) => (
  <Form.Group>
    <Form.Field className="element-details__field">
      <label>{description}</label>

      <ElementFieldType
        name={name}
        type={type}
        docRefTypes={docRefTypes}
        value={value}
        defaultValue={defaultValue}
      />
    </Form.Field>
    <Popup
      trigger={<Icon name="question circle" color="blue" size="large" />}
      content={
        <div>
          {defaultValue ? (
            <p>
              The <em>default value</em> is <strong>{defaultValue}</strong>.
            </p>
          ) : (
            <p>
              This property does not have a <em>default value</em>.
            </p>
          )}
          <p>
            The <em>field name</em> of this property is <strong>{name}</strong>
          </p>
        </div>
      }
    />
  </Form.Group>
));

ElementField.propTypes = {
  name: PropTypes.string.isRequired,
  description: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  docRefTypes: PropTypes.array,
  defaultValue: PropTypes.number.isRequired,
  value: PropTypes.object,
};

export default ElementField;
