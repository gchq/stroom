/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import { compose, lifecycle, branch, withProps, withHandlers, renderComponent } from 'recompose';
import { connect } from 'react-redux';

import Loader from 'components/Loader'
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
      renderComponent(() => <Loader message="Creating selectable item listing..." />),
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
