import React from 'react';
import PropTypes from 'prop-types';

import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { Form, Popup, Icon, Input, Checkbox } from 'semantic-ui-react';

import { Toggle, InputField } from 'react-semantic-redux-form';

import { Field } from 'redux-form';
import { actionCreators } from '../redux';

import { getActualValue } from './elementDetailsUtils';

import { DocPickerModal } from 'components/DocExplorer';
import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer/redux';

import NumericInput from 'prototypes/NumericInput';

const { pipelineElementPropertyUpdated } = actionCreators;

const { docRefPicked } = docExplorerActionCreators;

const getPickerName = settingName => `${settingName}_docRefModalPicker`;

const enhance = compose(
  connect(
    (state, props) => ({
      // state
    }),
    {
      docRefPicked,
      pipelineElementPropertyUpdated,
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

const ElementField = ({
  name,
  description,
  type,
  defaultValue,
  value,
  docRefTypes,
  pipelineId,
  pipelineElementPropertyUpdated,
  elementId,
}) => {
  let actualValue;
  let elementField;
  switch (type) {
    case 'boolean':
      actualValue = getActualValue(value, defaultValue, 'boolean');
      elementField = (
        <Checkbox
          toggle
          checked={actualValue}
          name={name}
          onChange={(_, event) => {
            console.log({ event });
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'boolean', event.checked);
          }}
        />
      );
      break;
    case 'int':
      actualValue = parseInt(getActualValue(value, defaultValue, 'integer'), 10);
      elementField = (
        <NumericInput
          value={actualValue}
          onChange={(newValue) => {
            console.log({ newValue });
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'integer', newValue);
          }}
        />
      );
      break;
    case 'DocRef':
      // TODO potential bug: I'm not sure why elementTypeProperties have multiple
      // docRefTypes, but we can only use one so we'll choose the first.
      // TODO Set this value when loading
      elementField = (
        <DocPickerModal
          pickerId={getPickerName(name)}
          typeFilter={docRefTypes[0]}
          onChange={(newValue) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'entity', newValue);
          }}
        />
      );
      break;
    case 'String':
      actualValue = getActualValue(value, defaultValue, 'string');
      elementField = (
        <Input
          value={actualValue}
          name={name}
          onChange={(_, event) =>
            pipelineElementPropertyUpdated(pipelineId, elementId, name, type, event.value)
          }
        />
      );
      break;
    case 'PipelineReference':
      // TODO
      break;
    default:
      actualValue = getActualValue(value, defaultValue, 'string');
      elementField = (
        <Input
          value={actualValue}
          name={name}
          onChange={(_, event) =>
            pipelineElementPropertyUpdated(pipelineId, elementId, name, type, event.value)
          }
        />
      );
      break;
  }

  return (
    <Form.Group>
      <Form.Field className="element-details__field">
        <label>{description}</label>
        {elementField}
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
};

ElementField.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  description: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  docRefTypes: PropTypes.array,
  defaultValue: PropTypes.number.isRequired,
  value: PropTypes.object,
  pipelineElementPropertyUpdated: PropTypes.func.isRequired,
};

export default enhance(ElementField);
