import React from 'react';

import { compose, lifecycle, branch, withProps, renderComponent } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react/dist/commonjs';

import { actionCreators } from './redux';
import withShortcutKeys from 'lib/withShortcutKeys';

const { selectableListingMounted, selectionUp, selectionDown } = actionCreators;

const upKeys = ['k', 'ctrl+k', 'up'];
const downKeys = ['j', 'ctrl+j', 'down'];
const openKeys = ['enter'];

const withSelectableItemListing = propsFunc =>
  compose(
    withProps((props) => {
      const {
        listingId, items, openItem, allowMultiSelect = false,
      } = propsFunc(props);

      return {
        listingId,
        items,
        allowMultiSelect,
        openItem,
      };
    }),
    connect(
      ({ selectableItemListings }, { listingId }) => ({
        selectableItemListing: selectableItemListings[listingId],
      }),
      {
        selectableListingMounted,
        selectionUp,
        selectionDown,
      },
    ),
    lifecycle({
      componentDidUpdate(prevProps, prevState, snapshot) {
        const {
          items, listingId, selectableListingMounted, allowMultiSelect,
        } = this.props;

        const itemsChanged = JSON.stringify(items) !== JSON.stringify(prevProps.items);

        if (itemsChanged) {
          selectableListingMounted(listingId, items, allowMultiSelect);
        }
      },
      componentDidMount() {
        const {
          selectableListingMounted, listingId, items, allowMultiSelect,
        } = this.props;

        selectableListingMounted(listingId, items, allowMultiSelect);
      },
    }),
    branch(
      ({ selectableItemListing }) => !selectableItemListing,
      renderComponent(() => <Loader active>Creating Selectable Item Listing</Loader>),
    ),
    withShortcutKeys([
      {
        mousetrapKeys: upKeys,
        keyEventMatcher: e => e.key === 'ArrowUp' || (e.ctrlKey && e.key === 'k'),
        action: ({ selectionUp, listingId }, e) => {
          selectionUp(listingId);
          if (e) e.preventDefault();
        },
      },
      {
        mousetrapKeys: downKeys,
        keyEventMatcher: e => e.key === 'ArrowDown' || (e.ctrlKey && e.key === 'j'),
        action: ({ selectionDown, listingId }, e) => {
          selectionDown(listingId);
          if (e) e.preventDefault();
        },
      },
      {
        mousetrapKeys: openKeys,
        keyEventMatcher: e => e.key === 'Enter',
        action: ({ openItem, selectableItemListing: { selectedItems } }, e) => {
          if (selectedItems.length === 1) {
            openItem(selectedItems[0]);
          }
          if (e) e.preventDefault();
        },
      },
    ]),
  );

export default withSelectableItemListing;
