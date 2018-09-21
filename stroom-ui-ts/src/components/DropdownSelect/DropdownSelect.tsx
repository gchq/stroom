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
  defaultStatePerId,
  StoreStatePerId,
  Handlers as SelectableItemListingHandlers
} from "../../lib/withSelectableItemListing";

import DefaultDropdownOption from "./DefaultDropdownOption";
import { DropdownOptionType, DropdownOptionProps } from "./DropdownOptionTypes";

export interface Props {
  pickerId: string;
  onChange: (x: string) => void;
  value: string;
  options: Array<DropdownOptionType>;
  OptionComponent?: React.ComponentType<DropdownOptionProps>;
}

export interface StateProps {
  textFocus: boolean;
  searchTerm: string;
}

export interface StateHandlers {
  onSearchFocus: () => StateProps;
  onSearchBlur: () => StateProps;
  onSearchTermChange: (x: any) => StateProps;
}

export interface StateUpdaters
  extends StateHandlerMap<StateProps>,
    StateHandlers {}

export interface ConnectState {
  valueToShow: string;
  selectableItemListing: StoreStatePerId;
}

export interface Handlers {
  onSearchKeyDown: React.ChangeEventHandler<HTMLInputElement>;
}

export interface EnhancedProps
  extends Props,
    StateHandlers,
    StateProps,
    ConnectState,
    SelectableItemListingHandlers,
    Handlers {}

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
        selectableItemListing:
          selectableItemListings[pickerId] || defaultStatePerId,
        options: optionsToUse
      };
    },
    {}
  ),
  withSelectableItemListing<DropdownOptionType>(
    ({ pickerId, options, onChange }: EnhancedProps) => ({
      listingId: pickerId,
      items: options,
      openItem: v => onChange(v.value),
      getKey: v => v.value
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
          inFocus={
            selectableItemListing.focussedItem &&
            selectableItemListing.focussedItem.value === option.value
          }
          onClick={() => onChange(option.value)}
          option={option}
        />
      ))}
    </div>
  </div>
);

export default enhance(DropdownSelect);
