import React from 'react';
import PropTypes from 'prop-types';
import { compose, withProps } from 'recompose';

import DocRefImage from 'components/DocRefImage';
import DropdownSelect from 'components/DropdownSelect';
import withDocRefTypes from './withDocRefTypes';

const DocRefTypeOption = ({
  selectableItemListing, inFocus, option: { text, value }, onClick,
}) => (
  <div className={`hoverable ${inFocus ? 'inFocus' : ''}`} onClick={onClick}>
    <DocRefImage size="sm" docRefType={value} />
    {text}
  </div>
);

const enhance = compose(
  withDocRefTypes,
  withProps(({ docRefTypes }) => ({
    options: docRefTypes.map(d => ({ text: d, value: d })),
  })),
);

let DocRefTypePicker = props => <DropdownSelect {...props} OptionComponent={DocRefTypeOption} />;

DocRefTypePicker = enhance(DocRefTypePicker);

DocRefTypePicker.propTypes = {
  pickerId: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  value: PropTypes.string.isRequired,
};

export default DocRefTypePicker;
