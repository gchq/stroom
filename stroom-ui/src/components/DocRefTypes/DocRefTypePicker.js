import React from 'react';
import PropTypes from 'prop-types';
import { compose, withStateHandlers, withState, withHandlers } from 'recompose';
import { connect } from 'react-redux';

import withDocRefTypes from './withDocRefTypes';
import DocRefTypeOption from './DocRefTypeOption';
import withSelectableItemListing, {
  defaultSelectableItemListingState,
} from 'lib/withSelectableItemListing';

const withSearchTerm = withState('searchTerm', 'onSearchTermChange', '');

const enhance = compose(
  withDocRefTypes,
  withSearchTerm,
  withStateHandlers(
    ({ initialTextFocus = false }) => ({
      textFocus: initialTextFocus,
    }),
    {
      onSearchFocus: () => e => ({
        textFocus: true,
      }),
      onSearchBlur: () => e => ({
        textFocus: false,
      }),
    },
  ),
  connect(({ selectableItemListings }, {
    pickerId, value, docRefTypes, searchTerm, textFocus,
  }) => {
    const selectableItemListing =
      selectableItemListings[pickerId] || defaultSelectableItemListingState;

    let docRefTypesToUse = docRefTypes;
    let valueToShow = value;

    if (textFocus) {
      valueToShow = searchTerm;
    }

    if (searchTerm.length > 0) {
      docRefTypesToUse = docRefTypes.filter(d =>
        d.toLowerCase().includes(searchTerm.toLowerCase()));
    }

    return {
      valueToShow,
      selectableItemListing,
      docRefTypes: docRefTypesToUse,
    };
  }, {}),
  withSelectableItemListing(({ pickerId, docRefTypes, onChange }) => ({
    listingId: pickerId,
    items: docRefTypes,
    openItem: onChange,
  })),
  withHandlers({
    onSearchKeyDown: ({ onSearchTermChange }) => ({ target: { value } }) =>
      onSearchTermChange(value),
  }),
);

let DocRefTypePicker = ({
  onChange,
  valueToShow,
  docRefTypes,
  onKeyDownWithShortcuts,
  selectableItemListing,
  onSearchFocus,
  onSearchBlur,
  onSearchKeyDown,
}) => (
  <div className="dropdown">
    <input
      onFocus={onSearchFocus}
      onBlur={onSearchBlur}
      placeholder="Select a type"
      value={valueToShow}
      onChange={onSearchKeyDown}
    />
    <div tabIndex={0} onKeyDown={onKeyDownWithShortcuts} className="dropdown__content">
      {docRefTypes.map(docRefType => (
        <DocRefTypeOption
          key={docRefType}
          onClick={() => onChange(docRefType)}
          docRefType={docRefType}
          selectableItemListing={selectableItemListing}
        />
      ))}
    </div>
  </div>
);

DocRefTypePicker = enhance(DocRefTypePicker);

DocRefTypePicker.propTypes = {
  pickerId: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  value: PropTypes.string.isRequired,
};

DocRefTypePicker.defaultProps = {
  value: [],
  onChange: v => console.log('Not implemented onChange, value ignored', v),
};

export default DocRefTypePicker;
