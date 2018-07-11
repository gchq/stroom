import React from 'react';
import PropTypes from 'prop-types';

import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { Form, Popup, Icon } from 'semantic-ui-react';

import { Toggle, InputField } from 'react-semantic-redux-form';

import { Field } from 'redux-form';

import { getActualValue } from './elementDetailsUtils';

import { DocPickerModal } from 'components/DocExplorer';
import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer/redux';

import NumericInput from 'prototypes/NumericInput';

const { docRefPicked } = docExplorerActionCreators;

const getPickerName = settingName => `${settingName}_docRefModalPicker`;

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
      if (this.props.value) {
        this.props.docRefPicked(getPickerName(this.props.name), this.props.value.value.entity);
      }
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
      return <DocPickerModal pickerId={getPickerName(name)} typeFilters={docRefTypes} />;
    case 'String':
    case 'PipelineReference':
      actualValue = getActualValue(value, defaultValue, 'string');
      return <Field name={name} component={InputField} value={actualValue} />;
    default:
      actualValue = getActualValue(value, defaultValue, 'string');
      return <Field name={name} component={InputField} value={actualValue} />;
  }
};

const ElementField = ({
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
);

ElementField.propTypes = {
  name: PropTypes.string.isRequired,
  description: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  docRefTypes: PropTypes.array,
  defaultValue: PropTypes.number.isRequired,
  value: PropTypes.object,
};

export default enhance(ElementField);
