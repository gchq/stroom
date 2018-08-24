import { openDocRef } from 'sections/RecentItems';
import { actionCreators as selectableItemActionCreators } from 'lib/withSelectableItemListing';

const { selectionUp, selectionDown } = selectableItemActionCreators;

const SHORTCUT_NAMES = {
  UP: 'up',
  DOWN: 'down',
  OPEN: 'open',
};

const FOCUSSED_ELEMENTS = {
  DOC_REF_LISTING: 'doc-ref-listing',
  DOC_REF_PICKER: 'doc-ref-picker',
};

const handleShortcutKey = (history, shortcut) => (dispatch, getState) => {
  const state = getState();
  const {
    keyIsDown: { focussedElement },
    selectableItemListings,
  } = state;

  if (!focussedElement) {
    return;
  }

  switch (shortcut) {
    case SHORTCUT_NAMES.UP:
      dispatch(selectionUp(focussedElement.id));
      break;
    case SHORTCUT_NAMES.DOWN:
      dispatch(selectionDown(focussedElement.id));
      break;
    case SHORTCUT_NAMES.OPEN:
      const selectableItemListing = selectableItemListings[focussedElement.id];
      if (selectableItemListing.selectedItems.length === 1) {
        const selectedDocRef = selectableItemListing.selectedItems[0];

        switch (focussedElement.type) {
          case FOCUSSED_ELEMENTS.DOC_REF_LISTING:
            dispatch(openDocRef(history, selectedDocRef));
            break;
          case FOCUSSED_ELEMENTS.DOC_REF_PICKER:
            break;
          default:
            break;
        }
      }
      break;
    default:
      break;
  }
};

export { SHORTCUT_NAMES, FOCUSSED_ELEMENTS, handleShortcutKey };
