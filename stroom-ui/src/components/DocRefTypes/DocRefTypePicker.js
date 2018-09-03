import React from 'react';
import PropTypes from 'prop-types';
import { Dropdown, Input } from 'semantic-ui-react';

import DocRefImage from 'components/DocRefImage';
import withDocRefTypes from './withDocRefTypes';

const DocRefTypePicker = ({ onChange, value, docRefTypes }) => (
  <Dropdown
    icon={null}
    trigger={<Input placeholder="Select a type" value={value || ''} onChange={() => {}} />}
  >
    <Dropdown.Menu>
      {docRefTypes.map(docRefType => (
        <Dropdown.Item key={docRefType} onClick={() => onChange(docRefType)}>
          <DocRefImage size="small" docRefType={docRefType} />
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

EnhancedDocRefTypePicker.defaultProps = {
  value: [],
  onChange: v => console.log('Not implemented onChange, value ignored', v),
};

export default EnhancedDocRefTypePicker;
