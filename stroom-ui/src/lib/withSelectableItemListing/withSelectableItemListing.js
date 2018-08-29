import React from 'react';

import { compose, lifecycle, branch, withProps, withHandlers, renderComponent } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react/dist/commonjs';

import { actionCreators } from './redux';

const { selectableListingMounted, selectionUp, selectionDown } = actionCreators;

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
    withHandlers({
      onKeyDownWithShortcuts: ({
        selectionUp,
        selectionDown,
        listingId,
        openItem,
        selectableItemListing: { selectedItems },
      }) => (e) => {
        if (e.key === 'ArrowUp' || (e.ctrlKey && e.key === 'k')) {
          selectionUp(listingId);
          e.preventDefault();
        } else if (e.key === 'ArrowDown' || (e.ctrlKey && e.key === 'j')) {
          selectionDown(listingId);
          e.preventDefault();
        } else if (e.key === 'Enter') {
          if (selectedItems.length === 1) {
            openItem(selectedItems[0]);
          }
          e.preventDefault();
        }
      },
    }),
  );

export default withSelectableItemListing;
