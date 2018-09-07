import React from 'react';

import { compose, lifecycle, branch, withProps, withHandlers, renderComponent } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react/dist/commonjs';

import { actionCreators, SELECTION_BEHAVIOUR } from './redux';

const {
  selectableListingMounted, selectFocussed, focusUp, focusDown,
} = actionCreators;

const isArraysEqual = (a, b) => {
  if (a && !b) return false;
  if (!a && b) return false;
  if (!a && !b) return true;

  if (a.length !== b.length) return false;

  return a.filter(aItem => !b.includes(aItem)).length === 0;
};

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

        const itemUuids = items ? items.map(d => d.uuid) : [];
        const prevUuids = prevProps.items ? prevProps.items.map(d => d.uuid) : [];

        if (!isArraysEqual(itemUuids, prevUuids)) {
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
        selectableItemListing,
        keyIsDown,
      }) => (e) => {
        if (e.key === 'ArrowUp' || e.key === 'k') {
          focusUp(listingId);
          e.preventDefault();
        } else if (e.key === 'ArrowDown' || e.key === 'j') {
          focusDown(listingId);
          e.preventDefault();
        } else if (e.key === 'Enter') {
          if (selectableItemListing.focussedItem) {
            openItem(selectableItemListing.focussedItem);
          }
          e.preventDefault();
        } else if (e.key === 'ArrowRight' || e.key === 'l') {
          if (selectableItemListing.focussedItem) {
            enterItem(selectableItemListing.focussedItem);
          }
        } else if (e.key === 'ArrowLeft' || e.key === 'h') {
          goBack(selectableItemListing.focussedItem);
        } else if (e.key === ' ') {
          if (selectableItemListing.selectionBehaviour !== SELECTION_BEHAVIOUR.NONE) {
            selectFocussed(listingId, keyIsDown);
            e.preventDefault();
          }
        }
      },
    }),
  );

export default withSelectableItemListing;
