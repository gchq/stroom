import React from 'react';

import { compose, lifecycle, branch, withProps, renderComponent } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react/dist/commonjs';
import Mousetrap from 'mousetrap';

import { actionCreators } from './redux';

const upKeys = ['k', 'ctrl+k', 'up'];
const downKeys = ['j', 'ctrl+j', 'down'];
const openKeys = ['enter'];

const {
  selectableListingMounted,
  selectableListingUnmounted,
  selectionUp,
  selectionDown,
} = actionCreators;

const withSelectableItemListing = propsFunc =>
  compose(
    withProps((props) => {
      const {
        openItem, listingId, items, allowMultiSelect = false,
      } = propsFunc(props);

      return {
        openItem,
        listingId,
        items,
        allowMultiSelect,
      };
    }),
    connect(
      ({ selectableItemListings }, { listingId }) => ({
        selectableItemListing: selectableItemListings[listingId],
      }),
      {
        selectableListingMounted,
        selectableListingUnmounted,
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
          selectableListingMounted,
          items,
          allowMultiSelect,
          selectionUp,
          selectionDown,
          listingId,
          openItem,
        } = this.props;

        selectableListingMounted(listingId, items, allowMultiSelect);

        Mousetrap.bind(upKeys, () => {
          selectionUp(listingId);
        });
        Mousetrap.bind(downKeys, () => {
          selectionDown(listingId);
        });
        Mousetrap.bind(openKeys, () => {
          // Need to re-read this from 'this' within this closure
          const {
            props: {
              openItem,
              selectableItemListing: { selectedItems },
            },
          } = this;
          if (selectedItems.length === 1) {
            openItem(selectedItems[0]);
          }
        });
      },
      componentWillUnmount() {
        const { selectableListingUnmounted, listingId } = this.props;
        Mousetrap.unbind(upKeys);
        Mousetrap.unbind(downKeys);
        Mousetrap.unbind(openKeys);
        selectableListingUnmounted(listingId);
      },
    }),
    branch(
      ({ selectableItemListing }) => !selectableItemListing,
      renderComponent(() => <Loader active>Creating Selectable Item Listing</Loader>),
    ),
  );

export default withSelectableItemListing;
