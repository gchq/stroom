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
      elementField = (
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

  const RevertToDefaultButton = <Button>Revert to default</Button>;
  const RevertToParentButton = <Button>Revert to parent</Button>;

  // If values are entities then they'll be objects, which we can't drop into JSX.
  // So we'll habitually use this function to get a usable display value.
  const getDisplayValue = value => (value !== null && typeof value === 'object' ? value.value : value);

  const isSet = value => value !== undefined && value !== '';

  // There's a matrix of outcomes depending on whether we have a value
  let popUpContentForValue;
  if (value === undefined && parentValue === undefined && isSet(defaultValue)) {
    valueSpec.valueToDisplay = defaultValue;
    popUpContentForValue = (
      <div>
        <p>
          This property is using the default value of <strong>{getDisplayValue(defaultValue)}</strong>.
        </p>
        <p>It is not inheriting anything and hasn't been set to anything by a user.</p>
      </div>
    );
  } else if (value !== undefined && parentValue === undefined && isSet(defaultValue)) {
    valueSpec.valueToDisplay = value.value[type];
    popUpContentForValue = (
      <div>
        <p>
          This property has a default value of <strong>{getDisplayValue(defaultValue)}</strong> but it has been overridden by the user. You can revert to the default if you like.
        </p>
        {RevertToDefaultButton}
        <p>This property is not inheriting anything.</p>
      </div>
    );
    valueSpec.resetToDefaultDeletesProperty = true;
  } else if (value === undefined && parentValue !== undefined && isSet(defaultValue)) {
    valueSpec.valueToDisplay = parentValue;
    popUpContentForValue = (
      <div>
        <p>
          This property has a default value of <strong>{getDisplayValue(defaultValue)}</strong>.
        </p>

        <p>
          It is currently inheriting a value of <strong>{getDisplayValue(parentValue)}</strong>, but you can make it use the default if you like. {RevertToDefaultButton}
        </p>
      </div>
    );
    valueSpec.resetToDefaultAddsRemove = true;
  } else if (value !== undefined && parentValue !== undefined && isSet(defaultValue)) {
    valueSpec.valueToDisplay = value.value[type];
    popUpContentForValue = (
      <div>
        <p>
          This property has a default value of <strong>{getDisplayValue(defaultValue)}</strong>.
        </p>
        <p>
          This property would inherit a value of <strong>{getDisplayValue(parentValue)}</strong> except this has been set by a user.
        </p>

        <p>
          You may revert it to the default or you may revert to the parent's value.
          {RevertToDefaultButton}
          {RevertToParentButton}
        </p>
      </div>
    );
    valueSpec.resetToDefaultDeletesProperty = true;
    valueSpec.resetToDefaultAddsRemove = true;
  } else if (value === undefined && parentValue === undefined && !isSet(defaultValue)) {
    valueSpec.valueToDisplay = undefined;
    popUpContentForValue = (
      <p>
        This property has no default value, it is not inheriting anything, and hasn't been set to anything by a user.
      </p>
    );
  } else if (value !== undefined && parentValue === undefined && !isSet(defaultValue)) {
    valueSpec.valueToDisplay = value.value[type];
    valueSpec.resetToDefaultDeletesProperty = true;
    popUpContentForValue = (
      <p>
        This property has no default value and it is not inheriting anything. It has been set by the user.
      </p>
    );
  } else if (value === undefined && parentValue !== undefined && !isSet(defaultValue)) {
    valueSpec.valueToDisplay = parentValue;
    popUpContentForValue = (
      <p>
        This property has no default value and has not been set to anything by the user, but it is inheriting a value of <strong>{getDisplayValue(parentValue)}</strong>.
      </p>
    );
    valueSpec.resetToDefaultAddsRemove = true;
  } else if (value !== undefined && parentValue !== undefined && !isSet(defaultValue)) {
    valueSpec.valueToDisplay = value.value[type];
    valueSpec.resetToDefaultDeletesProperty = true;
    valueSpec.resetToDefaultAddsRemove = true;
    popUpContentForValue = (
      <div>
        <p>This property has no default value.</p>

        <p>
          {' '}
          It is inheriting a value of <strong> {getDisplayValue(parentValue.value[type])}</strong> but this has been overriden by the user. You can revert to this inherited value if you like. {RevertToParentButton}
        </p>
      </div>
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
        trigger={<Icon name="cog" color="blue" size="large" />}
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
