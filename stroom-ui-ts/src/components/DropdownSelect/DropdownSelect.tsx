import * as React from "react";
import { connect } from "react-redux";
import {
  compose,
  withStateHandlers,
  withHandlers,
  StateHandlerMap
} from "recompose";

import { GlobalStoreState } from "../../startup/reducers";
import withSelectableItemListing, {
  defaultSelectableItemListingState,
  StoreStatePerId,
  AddedProps as SelectableItemListingAddedProps
} from "../../lib/withSelectableItemListing";

export interface DropdownOption {
  text: string;
  value: string;
}

export interface DropdownOptionProps {
  option: DropdownOption;
  inFocus: boolean;
  onClick: () => void;
}

const DefaultDropdownOption = ({
  option,
  inFocus,
  onClick
}: DropdownOptionProps) => (
  <div className={`hoverable ${inFocus ? "inFocus" : ""}`} onClick={onClick}>
    {option.text}
  </div>
);

export interface Props {
  pickerId: string;
  onChange: (x: string) => void;
  value: string;
  options: Array<DropdownOption>;
  OptionComponent: React.StatelessComponent<DropdownOptionProps>;
}

export interface StateProps {
  textFocus: boolean;
  searchTerm: string;
}

export interface StateFunctions {
  onSearchFocus: () => StateProps;
  onSearchBlur: () => StateProps;
  onSearchTermChange: (x: any) => StateProps;
}

export interface StateUpdaters
  extends StateHandlerMap<StateProps>,
    StateFunctions {}

export interface Handlers {
  onSearchKeyDown: React.ChangeEventHandler<HTMLInputElement>;
}

export interface EnhancedProps
  extends Props,
    StateFunctions,
    StateProps,
    Handlers,
    SelectableItemListingAddedProps {
  valueToShow: string;
  selectableItemListing: StoreStatePerId;
}

const enhance = compose<EnhancedProps, Props>(
  withStateHandlers<StateProps, StateUpdaters>(
    ({ textFocus = false, searchTerm = "" }: StateProps) => ({
      textFocus,
      searchTerm
    }),
    {
      onSearchFocus: () => e => ({
        textFocus: true
      }),
      onSearchBlur: () => e => ({
        textFocus: false
      }),
      onSearchTermChange: () => searchTerm => ({
        searchTerm
      })
    }
  ),
  connect(
    (
      { selectableItemListings }: GlobalStoreState,
      { pickerId, value, options, searchTerm, textFocus }: EnhancedProps
    ) => {
      const selectableItemListing =
        selectableItemListings[pickerId] || defaultSelectableItemListingState;

      let optionsToUse = options;
      let valueToShow = value;

      if (textFocus) {
        valueToShow = searchTerm;
      }

      if (searchTerm.length > 0) {
        optionsToUse = options.filter(d =>
          d.text.toLowerCase().includes(searchTerm.toLowerCase())
        );
      }

      return {
        valueToShow,
        selectableItemListing,
        options: optionsToUse
      };
    },
    {}
  ),
  withSelectableItemListing<DropdownOption>(
    ({ pickerId, options, onChange }: EnhancedProps) => ({
      listingId: pickerId,
      items: options.map(o => o.value),
      openItem: v => onChange(v),
      getKey: v => v
    })
  ),
  withHandlers<EnhancedProps, Handlers>({
    onSearchKeyDown: ({ onSearchTermChange }: EnhancedProps) => ({
      target: { value }
    }) => onSearchTermChange(value)
  })
);

let DropdownSelect = ({
  onSearchFocus,
  onSearchBlur,
  valueToShow,
  onSearchKeyDown,
  onKeyDownWithShortcuts,
  options,
  OptionComponent = DefaultDropdownOption,
  onChange,
  value,
  selectableItemListing
}: EnhancedProps) => (
  <div className="dropdown">
    <input
      onFocus={onSearchFocus}
      onBlur={onSearchBlur}
      placeholder="Select a type"
      value={valueToShow}
      onChange={onSearchKeyDown}
    />
    <div
      tabIndex={0}
      onKeyDown={onKeyDownWithShortcuts}
      className="dropdown__content"
    >
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

export default enhance(DropdownSelect);
