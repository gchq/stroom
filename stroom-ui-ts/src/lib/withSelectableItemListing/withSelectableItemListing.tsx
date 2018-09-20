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

import * as React from "react";
import {
  compose,
  lifecycle,
  branch,
  withProps,
  withHandlers,
  renderComponent
} from "recompose";
import { connect } from "react-redux";

import { GlobalStoreState } from "../../startup/reducers";
import Loader from "../../components/Loader";
import { actionCreators, SelectionBehaviour } from "./redux";

const {
  selectableListingMounted,
  selectFocussed,
  focusUp,
  focusDown
} = actionCreators;

const isArraysEqual = (a: Array<any>, b: Array<any>) => {
  if (a && !b) return false;
  if (!a && b) return false;
  if (!a && !b) return true;

  if (a.length !== b.length) return false;

  return a.filter(aItem => b.indexOf(aItem) === -1).length === 0;
};

export interface Props<TItem> {
  listingId: string;
  getKey: (x: TItem) => string;
  items: Array<TItem>;
  openItem: (i: TItem) => void;
  enterItem?: (i: TItem) => void;
  goBack?: () => void;
  selectionBehaviour?: SelectionBehaviour;
}

export type PropsFunc<TItem> = (a: any) => Props<TItem>;

export interface Handlers {
  onKeyDownWithShortcuts: React.KeyboardEventHandler<HTMLDivElement>;
}

export interface LifecycleProps<TItem> extends Props<TItem>, Handlers {
  selectableListingMounted: typeof selectableListingMounted;
}

const withSelectableItemListing = <TItem extends any>(
  propsFunc: PropsFunc<TItem>
) =>
  compose<LifecycleProps<TItem>, Props<TItem>>(
    withProps(props => {
      const {
        listingId,
        getKey,
        items,
        openItem,
        enterItem,
        goBack,
        selectionBehaviour = SelectionBehaviour.NONE
      } = propsFunc(props);

      return {
        listingId,
        getKey,
        items,
        selectionBehaviour,
        openItem,
        enterItem: enterItem || openItem,
        goBack: goBack || (() => console.log("Going back not implemented"))
      };
    }),
    connect(
      (
        { selectableItemListings, keyIsDown }: GlobalStoreState,
        { listingId }: Props<TItem>
      ) => ({
        selectableItemListing: selectableItemListings.byId[listingId],
        keyIsDown
      }),
      {
        selectableListingMounted,
        selectFocussed,
        focusUp,
        focusDown
      }
    ),
    lifecycle<LifecycleProps<TItem>, {}>({
      componentDidUpdate(prevProps, prevState) {
        const {
          selectableListingMounted,
          listingId,
          items,
          selectionBehaviour,
          getKey
        } = this.props;

        const itemUuids = items ? items.map(d => getKey(d)) : [];
        const prevUuids = prevProps.items
          ? prevProps.items.map(d => getKey(d))
          : [];

        if (!isArraysEqual(itemUuids, prevUuids)) {
          selectableListingMounted(
            listingId,
            items,
            selectionBehaviour,
            getKey
          );
        }
      },
      componentDidMount() {
        const {
          selectableListingMounted,
          listingId,
          items,
          selectionBehaviour,
          getKey
        } = this.props;

        selectableListingMounted(listingId, items, selectionBehaviour, getKey);
      }
    }),
    branch(
      ({ selectableItemListing }) => !selectableItemListing,
      renderComponent(() => (
        <Loader message="Creating selectable item listing..." />
      ))
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
        keyIsDown
      }) => (e: React.KeyboardEvent) => {
        if (e.key === "ArrowUp" || e.key === "k") {
          focusUp(listingId);
          e.preventDefault();
        } else if (e.key === "ArrowDown" || e.key === "j") {
          focusDown(listingId);
          e.preventDefault();
        } else if (e.key === "Enter") {
          if (selectableItemListing.focussedItem) {
            openItem(selectableItemListing.focussedItem);
          }
          e.preventDefault();
        } else if (e.key === "ArrowRight" || e.key === "l") {
          if (selectableItemListing.focussedItem) {
            enterItem(selectableItemListing.focussedItem);
          }
        } else if (e.key === "ArrowLeft" || e.key === "h") {
          goBack(selectableItemListing.focussedItem);
        } else if (e.key === " ") {
          if (
            selectableItemListing.selectionBehaviour !== SelectionBehaviour.NONE
          ) {
            selectFocussed(listingId, keyIsDown);
            e.preventDefault();
          }
        }
      }
    })
  );

export default withSelectableItemListing;
