import React from 'react';
import PropTypes from 'prop-types';

import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { Form, Popup, Icon, Input, Checkbox, Button } from 'semantic-ui-react';

import { actionCreators } from '../redux';

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

/**
 * Gets a value for display from the property.
 * 
 * @param {property} value The property
 * @param {string} type The type of the property
 */
const getDisplayValue = (value, type) => {
  // If values are entities then they'll be objects, which we can't drop into JSX.
  let displayValue = value !== null && typeof value === 'object' ? value.value : value;
  // And if we're dealing with a boolean then we'll want to get the string equivelent.  
  if(type === 'boolean'){
    displayValue = value.toString()
  }
  return displayValue;
}

/**
 * Gets the details for display and processing.
 * 
 * There's a matrix of outcomes depending on what value we have, and this function
 * produces output specific to the combination of these values.
 * 
 * @param {property} value The property from the top of the stack
 * @param {property} parentValue The property from the parent
 * @param {string} defaultValue The default property
 * @param {string} type The type of the property
 */
const getDetails = (value, parentValue, defaultValue, type) => {
  const RevertToDefaultButton = <Button>Revert to default</Button>;
  const RevertToParentButton = <Button>Revert to parent</Button>;

  // Parse the value if it's a boolean.
  if (type === 'boolean') {
    defaultValue = defaultValue === 'true';
  }

  // The property.value object uses integer so we might need to convert
  type = type === 'int' ? 'integer' : type;

  const isSet = value => value !== undefined && value !== '';

  let actualValue;
  let info;
  let resetToDefaultDeletesProperty = false;
  let resetToDefaultAddsRemove = false;

  if (value === undefined && parentValue === undefined && isSet(defaultValue)) {
    actualValue = defaultValue;
    info = (
      <div>
        <p>
          This property is using the default value of <strong>{getDisplayValue(defaultValue, type)}</strong>.
        </p>
        <p>It is not inheriting anything and hasn't been set to anything by a user.</p>
      </div>
    );
  } else if (value !== undefined && parentValue === undefined && isSet(defaultValue)) {
    actualValue = value.value[type];
    resetToDefaultDeletesProperty = true;
    info = (
      <div>
        <p>
          This property has a default value of <strong>{getDisplayValue(defaultValue, type)}</strong> but
          it has been overridden by the user. You can revert to the default if you like.
        </p>
        {RevertToDefaultButton}
        <p>This property is not inheriting anything.</p>
      </div>
    );
    
  } else if (value === undefined && parentValue !== undefined && isSet(defaultValue)) {
    actualValue = parentValue.value[type];
    resetToDefaultAddsRemove = true;
    info = (
      <div>
        <p>
          This property has a default value of <strong>{getDisplayValue(defaultValue, type)}</strong>.
        </p>

        <p>
          It is currently inheriting a value of <strong>{getDisplayValue(parentValue.value[type], type)}</strong>, but
          you can make it use the default if you like. {RevertToDefaultButton}
        </p>
      </div>
    );
  } else if (value !== undefined && parentValue !== undefined && isSet(defaultValue)) {
    actualValue = value.value[type];
    resetToDefaultDeletesProperty = true;
    resetToDefaultAddsRemove = true;
    info = (
      <div>
        <p>
          This property has a default value of <strong>{getDisplayValue(defaultValue, type)}</strong>.
        </p>
        <p>
          This property would inherit a value of <strong>{getDisplayValue(parentValue.value[type], type)}</strong>{' '}
          except this has been set by a user.
        </p>

        <p>
          You may revert it to the default or you may revert to the parent's value.
          {RevertToDefaultButton}
          {RevertToParentButton}
        </p>
      </div>
    );

  } else if (value === undefined && parentValue === undefined && !isSet(defaultValue)) {
    actualValue = undefined;
    info = (
      <p>
        This property has no default value, it is not inheriting anything, and hasn't been set to
        anything by a user.
      </p>
    );
  } else if (value !== undefined && parentValue === undefined && !isSet(defaultValue)) {
    actualValue = value.value[type];
    resetToDefaultDeletesProperty = true;
    info = (
      <p>
        This property has no default value and it is not inheriting anything. It has been set by the
        user.
      </p>
    );
  } else if (value === undefined && parentValue !== undefined && !isSet(defaultValue)) {
    actualValue = parentValue.value[type];
    resetToDefaultAddsRemove = true;
    info = (
      <p>
        This property has no default value and has not been set to anything by the user, but it is
        inheriting a value of <strong>{getDisplayValue(parentValue.value[type], type)}</strong>.
      </p>
    );
  } else if (value !== undefined && parentValue !== undefined && !isSet(defaultValue)) {
    actualValue = value.value[type];
    resetToDefaultDeletesProperty = true;
    resetToDefaultAddsRemove = true;
    info = (
      <div>
        <p>This property has no default value.</p>

        <p>
          It is inheriting a value of <strong> {getDisplayValue(parentValue.value[type], type)}</strong>
          but this has been overriden by the user. You can revert to this inherited value if you
          like. {RevertToParentButton}
        </p>
      </div>
    );
  }

  return {actualValue, info, resetToDefaultDeletesProperty, resetToDefaultAddsRemove};
};

/**
 * 
 * @param {property} value The property to get a field for
 * @param {string} name The name of the property
 * @param {string} pipelineId The ID of the pipeline this property's element belongs to
 * @param {string} elementId The ID of the element this property belongs to
 * @param {string} type The type of the element
 * @param {array} docRefTypes The docref types to filter by
 */
const getField = (pipelineElementPropertyUpdated, value, name, pipelineId, elementId, type, docRefTypes) => {
  let elementField;
  switch (type) {
    case 'boolean':
      elementField = (
        <Checkbox
          toggle
          checked={value}
          name={name}
          onChange={(_, event) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'boolean', event.checked);
          }}
        />
      );
      break;
    case 'int':
      elementField = (
        <NumericInput
          value={parseInt(value)}
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
      elementField = (
        <Input
          value={value}
          name={name}
          onChange={(_, event) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, type, event.value)
          }}
        />
      );
      break;
    case 'PipelineReference':
      elementField = <div>TODO</div>;
      break;
    default:
      elementField = (
        <Input
          value={value}
          name={name}
          onChange={(_, event) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, type, event.value)
          }}
        />
      );
      break;
  }
  return elementField;
};


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

  const details = getDetails(value, parentValue, defaultValue, type);
  const field = getField(pipelineElementPropertyUpdated, details.actualValue, name, pipelineId, elementId, type, docRefTypes)
  
  const popOverContent = (
    <div>
      <p>
        The <em>field name</em> of this property is <strong>{name}</strong>
      </p>
      {details.info}
    </div>
  );

  return (
    <Form.Group>
      <Form.Field className="element-details__field">
        <label>{description}</label>
        {field}
      </Form.Field>
      <Popup
        hoverable
        trigger={<Icon name="setting" color="blue" size="large" />}
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
