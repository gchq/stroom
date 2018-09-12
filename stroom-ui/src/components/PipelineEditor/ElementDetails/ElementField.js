import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { actionCreators } from '../redux';

import Tooltip from 'components/Tooltip';
import AppSearchBar from 'components/AppSearchBar';

const {
  pipelineElementPropertyUpdated,
  pipelineElementPropertyRevertToParent,
  pipelineElementPropertyRevertToDefault,
} = actionCreators;

const getPickerName = settingName => `${settingName}_docRefModalPicker`;

const enhance = compose(connect(
  (state, props) => ({
    // state
  }),
  {
    pipelineElementPropertyUpdated,
    pipelineElementPropertyRevertToParent,
    pipelineElementPropertyRevertToDefault,
  },
));

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
  if (type === 'boolean') {
    displayValue = value.toString();
  }
  return displayValue;
};

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
const getDetails = ({
  pipelineId,
  elementId,
  name,
  value,
  parentValue,
  childValue,
  defaultValue,
  type,
  pipelineElementPropertyRevertToParent,
  pipelineElementPropertyRevertToDefault,
}) => {
  const RevertToDefaultButton = (
    <button onClick={() => pipelineElementPropertyRevertToDefault(pipelineId, elementId, name)}>
      Revert to default
    </button>
  );
  const RevertToParentButton = (
    <button onClick={() => pipelineElementPropertyRevertToParent(pipelineId, elementId, name)}>
      Revert to parent
    </button>
  );

  // Parse the value if it's a boolean.
  if (type === 'boolean') {
    defaultValue = defaultValue === 'true';
  }

  // The property.value object uses integer so we might need to convert
  type = type === 'int' ? 'integer' : type;

  const isSet = value => value !== undefined && value !== '';

  let actualValue;
  let info;

  // We never use the parentValue to set the actualValue -- if there's a parentValue then it'll appear in
  // the merged picture. We just need the parentValue so we know where we are regards inheritance.
  // This doesn't apply with the defaultValue -- it is never in the merged picture so if
  // we deduce that's the value we want we need to set it as such.

  if (value === undefined && parentValue === undefined && isSet(defaultValue)) {
    actualValue = defaultValue;
    info = (
      <div>
        <p>
          This property is using the default value of{' '}
          <strong>{getDisplayValue(defaultValue, type)}</strong>.
        </p>
        <p>It is not inheriting anything and hasn't been set to anything by a user.</p>
      </div>
    );
  } else if (value !== undefined && parentValue === undefined && isSet(defaultValue)) {
    actualValue = value.value[type];
    info = (
      <div>
        <p>
          This property has a default value of{' '}
          <strong>{getDisplayValue(defaultValue, type)}</strong> but it has been overridden by the
          user. You can revert to the default if you like.
        </p>
        {RevertToDefaultButton}
        <p>This property is not inheriting anything.</p>
      </div>
    );
  } else if (value === undefined && parentValue !== undefined && isSet(defaultValue)) {
    actualValue = defaultValue;
    info = (
      <div>
        <p>
          This property is currently set to the default value. It's parent has a value of{' '}
          <strong>{getDisplayValue(parentValue.value[type], type)}</strong>. You may revert to this
          if you wish.
        </p>
        {RevertToParentButton}
      </div>
    );
  } else if (value !== undefined && parentValue !== undefined && isSet(defaultValue)) {
    actualValue = value.value[type];
    const setByChild = childValue !== undefined && childValue.value[type] === value.value[type];
    if (setByChild) {
      info = (
        <div>
          <p>
            This property has a default value of{' '}
            <strong>{getDisplayValue(defaultValue, type)}</strong>.
          </p>
          <p>
            This property would inherit a value of{' '}
            <strong>{getDisplayValue(parentValue.value[type], type)}</strong> except this has been
            set by a user.
          </p>

          <p>You may revert it to the default or you may revert to the parent's value</p>
          <div>
            {RevertToDefaultButton}
            {RevertToParentButton}
          </div>
        </div>
      );
    } else {
      info = (
        <div>
          <p>
            This property has a default value of{' '}
            <strong>{getDisplayValue(defaultValue, type)}</strong>.
          </p>
          <p>This property is using an inherited value.</p>

          <p>You may revert it to the default if you wish.</p>
          <div>{RevertToDefaultButton}</div>
        </div>
      );
    }
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
    info = (
      <p>
        This property has no default value and it is not inheriting anything. It has been set by the
        user.
      </p>
    );
  } else if (value === undefined && parentValue !== undefined && !isSet(defaultValue)) {
    actualValue = undefined;
    info = (
      <p>
        This property has no default value and has not been set to anything by the user, but it is
        inheriting a value of <strong>{getDisplayValue(parentValue.value[type], type)}</strong>.
      </p>
    );
  } else if (value !== undefined && parentValue !== undefined && !isSet(defaultValue)) {
    actualValue = value.value[type];
    const setByChild = childValue !== undefined && childValue.value[type] === value.value[type];
    if (setByChild) {
      info = (
        <div>
          <p>This property has no default value.</p>

          <p>
            It is inheriting a value of{' '}
            <strong> {getDisplayValue(parentValue.value[type], type)}</strong>
            but this has been overriden by the user. You can revert to this inherited value if you
            like.
          </p>
          {RevertToParentButton}
        </div>
      );
    } else {
      info = (
        <div>
          <p>This property has no default value.</p>

          <p>This property is inheriting it's value.</p>
        </div>
      );
    }
  }

  return { actualValue, info };
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
const FieldValue = ({
  pipelineElementPropertyUpdated,
  value,
  name,
  pipelineId,
  elementId,
  type,
  docRefTypes,
}) => {
  let elementField;
  switch (type) {
    case 'boolean':
      elementField = (
        <input
          type="checkbox"
          checked={value}
          name={name}
          onChange={() => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'boolean', !value);
          }}
        />
      );
      break;
    case 'int':
      elementField = (
        <input
          type="number"
          name={name}
          value={parseInt(value, 10)}
          onChange={({ target: { value } }) => {
            pipelineElementPropertyUpdated(
              pipelineId,
              elementId,
              name,
              'integer',
              parseInt(value, 10),
            );
          }}
        />
      );
      break;
    case 'docref':
      elementField = (
        <AppSearchBar
          pickerId={getPickerName(name)}
          typeFilters={docRefTypes}
          value={value}
          onChange={(node) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'docref', node);
          }}
        />
      );

      break;

    case 'string':
      elementField = (
        <input
          value={value}
          name={name}
          onChange={({ target: { value } }) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, type, value);
          }}
        />
      );
      break;
    case 'pipelinereference':
      elementField = <div>TODO</div>;
      break;
    default:
      elementField = (
        <input
          value={value}
          name={name}
          onChange={({ target: { value } }) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, type, value);
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
  childValue,
  parentValue,
  docRefTypes,
  pipelineId,
  pipelineElementPropertyUpdated,
  pipelineElementPropertyRevertToParent,
  pipelineElementPropertyRevertToDefault,
  elementId,
  revertToDefault,
}) => {
  // Types should always be lower case.
  type = type.toLowerCase();

  const details = getDetails({
    value,
    parentValue,
    defaultValue,
    type,
    pipelineElementPropertyRevertToParent,
    pipelineElementPropertyRevertToDefault,
    elementId,
    name,
    pipelineId,
    childValue,
  });
  const field = (
    <FieldValue
      {...{
        pipelineElementPropertyUpdated,
        value: details.actualValue,
        name,
        pipelineId,
        elementId,
        type,
        docRefTypes,
      }}
    />
  );

  const popOverContent = (
    <div>
      {/* <p>
        <strong>Field name: </strong> {name}
      </p> */}
      <p>
        The <em>field name</em> of this property is <strong>{name}</strong>
      </p>
      {details.info}
    </div>
  );

  return (
    <div>
      <div className="element-details__field">
        <label>{description}</label>
        {field}
      </div>
      <Tooltip
        hoverable
        trigger={<FontAwesomeIcon icon="cog" color="blue" size="lg" />}
        content={popOverContent}
      />
    </div>
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
  childValue: PropTypes.object,
  parentValue: PropTypes.object,
  pipelineElementPropertyUpdated: PropTypes.func.isRequired,
};

export default enhance(ElementField);
