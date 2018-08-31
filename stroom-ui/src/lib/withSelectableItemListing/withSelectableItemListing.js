import React from 'react';

import { compose, lifecycle, branch, withProps, withHandlers, renderComponent } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react/dist/commonjs';

import { actionCreators, SELECTION_BEHAVIOUR } from './redux';

const {
  selectableListingMounted, selectFocussed, focusUp, focusDown,
} = actionCreators;

const withSelectableItemListing = propsFunc =>
  compose(
    withProps((props) => {
      const {
        listingId,
        items,
        openItem,
        enterItem,
        goBack,
        selectionBehaviour = SELECTION_BEHAVIOUR.NONE,
      } = propsFunc(props);

      return {
        listingId,
        items,
        selectionBehaviour,
        openItem,
        enterItem: enterItem || openItem,
        goBack: goBack || (() => console.log('Going back not implemented')),
      };
    }),
    connect(
      ({ selectableItemListings, keyIsDown }, { listingId }) => ({
        selectableItemListing: selectableItemListings[listingId],
        keyIsDown,
      }),
      {
        selectableListingMounted,
        selectFocussed,
        focusUp,
        focusDown,
      },
    ),
    lifecycle({
      componentDidUpdate(prevProps, prevState, snapshot) {
        const {
          items, listingId, selectableListingMounted, selectionBehaviour,
        } = this.props;

        const itemsChanged = JSON.stringify(items) !== JSON.stringify(prevProps.items);

        if (itemsChanged) {
          selectableListingMounted(listingId, items, selectionBehaviour);
        }
      },
      componentDidMount() {
        const {
          selectableListingMounted, listingId, items, selectionBehaviour,
        } = this.props;

        selectableListingMounted(listingId, items, selectionBehaviour);
      },
    }),
    branch(
      ({ selectableItemListing }) => !selectableItemListing,
      renderComponent(() => <Loader active>Creating Selectable Item Listing</Loader>),
    ),
    withHandlers({
      onKeyDownWithShortcuts: ({
        focusUp,
        focusDown,
        selectFocussed,
        listingId,
        openItem,
        enterItem,
        goBack,
        selectableItemListing: { selectedItems, focussedItem, selectionBehaviour },
        keyIsDown,
      }) => (e) => {
        if (e.key === 'ArrowUp' || (e.ctrlKey && e.key === 'k')) {
          focusUp(listingId);
          e.preventDefault();
        } else if (e.key === 'ArrowDown' || (e.ctrlKey && e.key === 'j')) {
          focusDown(listingId);
          e.preventDefault();
        } else if (e.key === 'Enter') {
          if (focussedItem) {
            openItem(focussedItem);
          }
          e.preventDefault();
        } else if (e.ctrlKey && (e.key === 'ArrowRight' || e.key === 'l')) {
          if (focussedItem) {
            enterItem(focussedItem);
          }
        } else if (e.ctrlKey && (e.key === 'ArrowLeft' || e.key === 'h')) {
          goBack();
        } else if (e.key === ' ') {
          if (selectionBehaviour !== SELECTION_BEHAVIOUR.NONE) {
            selectFocussed(listingId, keyIsDown);
            e.preventDefault();
          }
        }
      },
    }),
  );

export default withSelectableItemListing;
