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

// We need to prevent up and down keys from moving the cursor around in the input

// I'd rather use Mousetrap for these shortcut keys. Historically Mousetrap
// hasn't handled keypresses that occured inside inputs or textareas.
// There were some changes to fix this, like binding specifically
// to a field. But that requires getting the element from the DOM and
// we'd rather not break outside React to do this. The other alternative
// is adding 'mousetrap' as a class to the input, but that doesn't seem to work.

// Up
const upArrow = 38;
const k = 75;

// Down
const downArrow = 40;
const j = 74;

const enter = 13;

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
    withShortcutKeys()
      .beginShortcutAction()
      .withMousetrapKeys(upKeys)
      .withKeyEventMatcher(e => e.keyCode === upArrow || (e.ctrlKey && e.keyCode === k))
      .withAction(({ selectionUp, listingId }, e) => {
        selectionUp(listingId);
        if (e) e.preventDefault();
      })
      .endShortcutAction()

      .beginShortcutAction()
      .withMousetrapKeys(downKeys)
      .withKeyEventMatcher(e => e.keyCode === downArrow || (e.ctrlKey && e.keyCode === j))
      .withAction(({ selectionDown, listingId }, e) => {
        selectionDown(listingId);
        if (e) {
          e.preventDefault();
        }
      })
      .endShortcutAction()

      .beginShortcutAction()
      .withMousetrapKeys(openKeys)
      .withKeyEventMatcher(e => e.keyCode === enter)
      .withAction(({ openItem, selectableItemListing: { selectedItems } }, e) => {
        console.log('Open Shortcut Detected in Selectable Item List', selectedItems);
        if (selectedItems.length === 1) {
          openItem(selectedItems[0]);
        }
        if (e) e.preventDefault();
      })
      .endShortcutAction()

      .build(),
  );

export default withSelectableItemListing;
