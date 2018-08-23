import React from 'react';

import { compose, lifecycle, branch, withProps, renderComponent } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react/dist/commonjs';

import { actionCreators } from './redux';

const { selectableListingMounted } = actionCreators;

const withSelectableItemListing = propsFunc =>
  compose(
    withProps((props) => {
      const { listingId, items, allowMultiSelect = false } = propsFunc(props);

      return {
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
  );

export default withSelectableItemListing;
