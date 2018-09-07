import React from 'react';
import PropTypes from 'prop-types';

import { compose, withProps } from 'recompose';

import DocRefImage from 'components/DocRefImage';

const enhanceOption = compose(withProps(({ docRefType, selectableItemListing: { selectedItems = [], focussedItem } }) => {
  const isSelected = selectedItems.includes(docRefType);
  const inFocus = focussedItem === docRefType;

  const additionalClasses = [];
  if (isSelected) {
    additionalClasses.push('selected');
  }
  if (inFocus) {
    additionalClasses.push('inFocus');
  }
  const className = `hoverable ${additionalClasses.join(' ')}`;

  return {
    className,
  };
}));

let DocRefTypeOption = ({
  selectableItemListing, onClick, docRefType, className,
}) => (
  <div className={className} onClick={onClick}>
    <DocRefImage size="small" docRefType={docRefType} />
    {docRefType}
  </div>
);

DocRefTypeOption = enhanceOption(DocRefTypeOption);

DocRefTypeOption.propTypes = {
  onClick: PropTypes.func.isRequired,
  selectableItemListing: PropTypes.object.isRequired,
  docRefType: PropTypes.string.isRequired,
};

export default DocRefTypeOption;
