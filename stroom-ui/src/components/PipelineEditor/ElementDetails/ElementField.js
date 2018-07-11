import React from 'react';
import PropTypes from 'prop-types';

import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { Form, Popup, Icon, Input, Checkbox, Button } from 'semantic-ui-react';

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
  parentValue,
  docRefTypes,
  pipelineId,
  pipelineElementPropertyUpdated,
  elementId,
}) => {
  // Types should always be lower case.
  type = type.toLowerCase();

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
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'integer', newValue);
          }}
        />
      );
      break;
    case 'DocRef':
        <DocPickerModal
          pickerId={getPickerName(name)}
          typeFilter={docRefTypes}
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
      elementField = <div>TODO</div>;
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

  const valueSpec = {
    valueToDisplay: false,
    resetToDefaultDeletesProperty: false,
    resetToDefaultAddsRemove: false,
  };

  const revertToDefaultButton = <Button>Revert to default</Button>;
  const revertToParentButton = <Button>Revert to parent</Button>;

  // If values are entities then they'll be objects, which we can't drop into JSX.
  // So we'll habitually use this function to get a usable display value.
  const getDisplayValue = value =>
    (value !== null && typeof value === 'object' ? value.value : value);

  const isSet = value => value !== undefined && value !== '';

  // There's a matrix of outcomes depending on whether we have a value
  let popUpContentForValue;
  if (value === undefined && parentValue === undefined && isSet(defaultValue)) {
    valueSpec.valueToDisplay = defaultValue;
    popUpContentForValue = (
      <p>
        This property is using the default value of <em>{getDisplayValue(defaultValue)}</em>. It is
        not inheriting anything and hasn't been set to anything by a user.
      </p>
    );
  } else if (value !== undefined && parentValue === undefined && isSet(defaultValue)) {
    valueSpec.valueToDisplay = value.value[type];
    popUpContentForValue = (
      <div>
        <p>
          This property has a default value of <em>{getDisplayValue(defaultValue)}</em> but it has
          been overridden by the user. It is not inheriting anything. You can revert to the default
          if you like.
        </p>
        {revertToDefaultButton}
      </div>
    );
    valueSpec.resetToDefaultDeletesProperty = true;
  } else if (value === undefined && parentValue !== undefined && isSet(defaultValue)) {
    valueSpec.valueToDisplay = parentValue;
    popUpContentForValue = (
      <p>
        This property has a default value of <em>{getDisplayValue(defaultValue)}</em>. It is
        currently inheriting a value of <em>{getDisplayValue(parentValue)}</em>, but you can make it
        use the default if you like. TODO: REVERT TO DEFAULT BUTTON
      </p>
    );
    valueSpec.resetToDefaultAddsRemove = true;
  } else if (value !== undefined && parentValue !== undefined && isSet(defaultValue)) {
    valueSpec.valueToDisplay = value.value[type];
    popUpContentForValue = (
      <p>
        This property has a default value of <em>{getDisplayValue(defaultValue)}</em>. It would
        inherit a value of
        <em>{getDisplayValue(parentValue)}</em> except this has been set by a user to
        <em>{getDisplayValue(value.value[type])}</em>. You may revert it to the default or you may
        revert to the parent's value. TODO: REVERT TO DEFAULT BUTTON. TODO: REVERT TO PARENT BUTTON.
      </p>
    );
    valueSpec.resetToDefaultDeletesProperty = true;
    valueSpec.resetToDefaultAddsRemove = true;
  } else if (value === undefined && parentValue === undefined && !isSet(defaultValue)) {
    valueSpec.valueToDisplay = undefined;
    popUpContentForValue = (
      <p>
        This property has no default value, it is not inheriting anything, and hasn't been set to
        anything by a user.
      </p>
    );
  } else if (value !== undefined && parentValue === undefined && !isSet(defaultValue)) {
    valueSpec.valueToDisplay = value.value[type];
    valueSpec.resetToDefaultDeletesProperty = true;
    popUpContentForValue = (
      <p>
        This property has no default value and it is not inheriting anything. It has been set by the
        user.
      </p>
    );
  } else if (value === undefined && parentValue !== undefined && !isSet(defaultValue)) {
    valueSpec.valueToDisplay = parentValue;
    popUpContentForValue = (
      <p>
        This property has no default value and has not been set to anything by the user, but it is
        inheriting a value of <em>{getDisplayValue(parentValue)}</em>.
      </p>
    );
    valueSpec.resetToDefaultAddsRemove = true;
  } else if (value !== undefined && parentValue !== undefined && !isSet(defaultValue)) {
    valueSpec.valueToDisplay = value.value[type];
    valueSpec.resetToDefaultDeletesProperty = true;
    valueSpec.resetToDefaultAddsRemove = true;
    popUpContentForValue = (
      <p>
        This property has no default value. It is inheriting a value of{' '}
        <em>{getDisplayValue(parentValue)}</em> but this has been overriden by the user to{' '}
        <em>{getDisplayValue(value.value[type])}</em>. You can revert to the inherited value if you
        like. TODO: REVERT TO PARENT BUTTON.
      </p>
    );
  }

  const popOverContent = (
    <div>
      <p>
        The <em>field name</em> of this property is <strong>{name}</strong>
      </p>
      {popUpContentForValue}
    </div>
  );

  return (
    <Form.Group>
      <Form.Field className="element-details__field">
        <label>{description}</label>
        {elementField}
      </Form.Field>
      <Popup
        hoverable
        trigger={<Icon name="question circle" color="blue" size="large" />}
        content={popOverContent}
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
  defaultValue: PropTypes.string.isRequired,
  value: PropTypes.object,
  parentValue: PropTypes.object,
  pipelineElementPropertyUpdated: PropTypes.func.isRequired,
};

export default enhance(ElementField);
