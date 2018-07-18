import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { Dropdown } from 'semantic-ui-react';

import withDocRefTypes from './withDocRefTypes';

const DocRefTypePicker = ({
  onChange, value, docRefTypes, pickerId,
}) => (
  <Dropdown
    selection
    trigger={
      <span>
        {value && value.length > 0 ? (
          <img className="doc-ref__icon" alt="X" src={require(`./images/${value}.svg`)} />
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
          <img className="doc-ref__icon" alt="X" src={require(`./images/${docRefType}.svg`)} />
          {docRefType}
        </Dropdown.Item>
      ))}
    </Dropdown.Menu>
  </Dropdown>
);

const EnhancedDocRefTypePicker = withDocRefTypes(DocRefTypePicker);

EnhancedDocRefTypePicker.propTypes = {
  pickerId: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  value: PropTypes.string.isRequired,
};

export default EnhancedDocRefTypePicker;
