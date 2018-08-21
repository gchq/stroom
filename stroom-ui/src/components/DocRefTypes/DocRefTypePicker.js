import React from 'react';
import PropTypes from 'prop-types';
import { Dropdown, Input } from 'semantic-ui-react';

import withDocRefTypes from './withDocRefTypes';

const DocRefTypePicker = ({ onChange, value, docRefTypes }) => (
  <Dropdown
    icon={null}
    trigger={<Input placeholder="Select a type" value={value || ''} onChange={() => {}} />}
  >
    <Dropdown.Menu>
      {docRefTypes.map(docRefType => (
        <Dropdown.Item key={docRefType} onClick={() => onChange(docRefType)}>
          <img
            className="stroom-icon--small"
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
