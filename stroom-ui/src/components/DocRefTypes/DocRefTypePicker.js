import React from 'react';
import PropTypes from 'prop-types';
import { Dropdown } from 'semantic-ui-react';

import withDocRefTypes from './withDocRefTypes';

const DocRefTypePicker = ({ onChange, value, docRefTypes }) => (
  <Dropdown
    // it moans about mixing trigger and selection, but it's the only way to make it look right..?
    selection
    trigger={
      <span>
        {value && value.length > 0 ? (
          <img
            className="doc-ref__icon-small"
            alt="X"
            src={require(`../../images/docRefTypes/${value}.svg`)}
          />
        ) : (
          undefined
        )}
        {value}
      </span>
    }
  >
    <Dropdown.Menu>
      {docRefTypes.map(docRefType => (
        <Dropdown.Item key={docRefType} onClick={() => onChange(docRefType)}>
          <img
            className="doc-ref__icon-small"
            alt="X"
            src={require(`../../images/docRefTypes/${docRefType}.svg`)}
          />
          {docRefType}
        </Dropdown.Item>
      ))}
    </Dropdown.Menu>
  </Dropdown>
);

const EnhancedDocRefTypePicker = withDocRefTypes(DocRefTypePicker);

EnhancedDocRefTypePicker.propTypes = {
  onChange: PropTypes.func.isRequired,
  value: PropTypes.string.isRequired,
};

export default EnhancedDocRefTypePicker;
