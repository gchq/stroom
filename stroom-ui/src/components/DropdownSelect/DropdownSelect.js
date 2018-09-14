import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withStateHandlers, withHandlers } from 'recompose';

import DropdownOptionPropType from './DropdownOptionPropType';
import withSelectableItemListing, {
  defaultSelectableItemListingState,
} from 'lib/withSelectableItemListing';

const enhance = compose(
  withStateHandlers(
    ({ textFocus = false, searchTerm = '' }) => ({
      textFocus,
      searchTerm,
    }),
    {
      onSearchFocus: () => e => ({
        textFocus: true,
      }),
      onSearchBlur: () => e => ({
        textFocus: false,
      }),
      onSearchTermChange: () => searchTerm => ({
        searchTerm,
      }),
    },
  ),
  connect(({ selectableItemListings }, {
    pickerId, value, options, searchTerm, textFocus,
  }) => {
    const selectableItemListing =
      selectableItemListings[pickerId] || defaultSelectableItemListingState;

    let optionsToUse = options;
    let valueToShow = value;

    if (textFocus) {
      valueToShow = searchTerm;
    }

    if (searchTerm.length > 0) {
      optionsToUse = options.filter(d => d.text.toLowerCase().includes(searchTerm.toLowerCase()));
    }

    return {
      valueToShow,
      selectableItemListing,
      options: optionsToUse,
    };
  }, {}),
  withSelectableItemListing(({ pickerId, options, onChange }) => ({
    listingId: pickerId,
    items: options.map(o => o.value),
    openItem: v => onChange(v),
    getKey: v => v,
  })),
  withHandlers({
    onSearchKeyDown: ({ onSearchTermChange }) => ({ target: { value } }) =>
      onSearchTermChange(value),
  }),
);

let DropdownSelect = ({
  onSearchFocus,
  onSearchBlur,
  valueToShow,
  onSearchKeyDown,
  onKeyDownWithShortcuts,
  options,
  OptionComponent,
  onChange,
  value,
  selectableItemListing,
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
      {options.map(option => (
        <OptionComponent
          key={option.value}
          inFocus={selectableItemListing.focussedItem === option.value}
          onClick={() => onChange(option.value)}
          option={option}
        />
      ))}
    </div>
  </div>
);

DropdownSelect = enhance(DropdownSelect);

DropdownSelect.propTypes = {
  pickerId: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  value: DropdownOptionPropType.isRequired,
  options: PropTypes.arrayOf(DropdownOptionPropType),
  OptionComponent: PropTypes.func.isRequired,
};

const DefaultDropdownOption = ({ option, inFocus, onClick }) => (
  <div className={`hoverable ${inFocus ? 'inFocus' : ''}`} onClick={onClick}>
    {option.text}
  </div>
);

DropdownSelect.defaultProps = {
  OptionComponent: DefaultDropdownOption,
};

export default DropdownSelect;
