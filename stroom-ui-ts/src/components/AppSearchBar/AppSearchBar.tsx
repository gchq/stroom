import * as React from "react";

import {
  compose,
  withHandlers,
  withProps,
  withStateHandlers,
  StateHandlerMap,
  StateHandler
} from "recompose";
import { connect } from "react-redux";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import { findItem, filterTree } from "../../lib/treeUtils";
import {
  actionCreators as appSearchBarActionCreators,
  defaultPickerState,
  SEARCH_MODE
} from "./redux";
import { DocRefType } from "../../types";
import { searchApp } from "../FolderExplorer/explorerClient";
import { DocRefBreadcrumb } from "../DocRefBreadcrumb";
import DocRefListingEntry from "../DocRefListingEntry";
import withSelectableItemListing, {
  defaultSelectableItemListingState
} from "../../lib/withSelectableItemListing";
import withDocumentTree from "../FolderExplorer/withDocumentTree";

import ModeOptionButtons from "./ModeOptionButtons";

const { searchTermUpdated, navigateToFolder } = appSearchBarActionCreators;

interface FocusStateProps {
  textFocus: boolean;
}

type FocusStateHandlerProps = StateHandlerMap<FocusStateProps> & {
  onSearchFocus(): StateHandler<FocusStateProps>;
  onSearchBlur(): StateHandler<FocusStateProps>;
};

interface OwnProps {
  pickerId: string;
  typeFilters: [];
  onChange: (d: DocRefType) => any;
  value: DocRefType;
  className?: string;
}

interface StateProps {}

interface DispatchProps {}

const enhance = compose(
  withDocumentTree,
  withStateHandlers<FocusStateProps, FocusStateHandlerProps>(
    _ => ({
      textFocus: false
    }),
    {
      onSearchFocus: () => e => ({
        textFocus: true
      }),
      onSearchBlur: () => e => ({
        textFocus: false
      })
    }
  ),
  connect<StateProps, DispatchProps, OwnProps>(
    (
      {
        appSearch,
        recentItems,
        selectableItemListings,
        folderExplorer: { documentTree }
      },
      { pickerId, typeFilters, value, textFocus }
    ) => {
      const appSearchForPicker = appSearch[pickerId] || defaultPickerState;
      const selectableItemListing =
        selectableItemListings[pickerId] || defaultSelectableItemListingState;
      const {
        searchTerm,
        navFolder,
        searchResults,
        searchMode
      } = appSearchForPicker;
      const documentTreeToUse =
        typeFilters.length > 0
          ? filterTree(documentTree, d => typeFilters.includes(d.type))
          : documentTree;

      let docRefs;
      let thisFolder;
      let parentFolder;
      let valueToShow;

      if (textFocus) {
        valueToShow = searchTerm;
      } else if (value) {
        valueToShow = value.name;
      } else {
        valueToShow = "";
      }

      switch (searchMode) {
        case SEARCH_MODE.NAVIGATION: {
          const navFolderToUse = navFolder || documentTreeToUse;
          const navFolderWithLineage = findItem(
            documentTreeToUse,
            navFolderToUse.uuid
          );
          docRefs = navFolderWithLineage.node.children;
          thisFolder = navFolderWithLineage.node;

          if (
            navFolderWithLineage.lineage &&
            navFolderWithLineage.lineage.length > 0
          ) {
            parentFolder =
              navFolderWithLineage.lineage[
                navFolderWithLineage.lineage.length - 1
              ];
          }
          break;
        }
        case SEARCH_MODE.GLOBAL_SEARCH: {
          docRefs = searchResults;
          break;
        }
        case SEARCH_MODE.RECENT_ITEMS: {
          docRefs = recentItems;
          break;
        }
        default:
          docRefs = [];
          break;
      }

      return {
        searchTerm,
        searchMode,
        valueToShow,
        selectableItemListing,
        docRefs,
        hasNoResults: docRefs.length === 0,
        noResultsText:
          searchMode === SEARCH_MODE.NAVIGATION ? "empty" : "no results",
        provideBreadcrumbs: searchMode !== SEARCH_MODE.NAVIGATION,
        thisFolder,
        parentFolder
      };
    },
    {
      searchApp,
      searchTermUpdated,
      navigateToFolder
    }
  ),
  withHandlers({
    // Prevent folders being selected if they aren't actually valid selections
    onChange: ({
      onChange,
      typeFilters,
      pickerId,
      navigateToFolder
    }) => docRef => {
      if (docRef.type === "Folder") {
        if (typeFilters.length === 0 || typeFilters.includes("Folder")) {
          onChange(docRef);
        } else {
          navigateToFolder(pickerId, docRef);
        }
      } else {
        onChange(docRef);
      }
    }
  }),
  withSelectableItemListing(
    ({ pickerId, docRefs, navigateToFolder, parentFolder, onChange }) => ({
      listingId: pickerId,
      items: docRefs,
      openItem: onChange,
      getKey: d => d.uuid,
      enterItem: d => navigateToFolder(pickerId, d),
      goBack: () => navigateToFolder(pickerId, parentFolder)
    })
  ),
  withHandlers({
    onSearchTermChange: ({ pickerId, searchTermUpdated, searchApp }) => ({
      target: { value }
    }) => {
      searchTermUpdated(pickerId, value);
      searchApp(pickerId, { term: value });
    },
    thisNavigateToFolder: ({ navigateToFolder, pickerId }) => d =>
      navigateToFolder(pickerId, d)
  }),
  withProps(
    ({
      pickerId,
      searchMode,
      thisFolder,
      parentFolder,
      thisNavigateToFolder,
      searchTerm
    }) => {
      let headerTitle;
      let headerIcon;
      let headerAction = () => {};

      switch (searchMode) {
        case SEARCH_MODE.NAVIGATION: {
          headerTitle = thisFolder.name;
          if (parentFolder) {
            headerIcon = "arrow-left";
            headerAction = () => thisNavigateToFolder(parentFolder);
          } else {
            headerIcon = "folder";
          }
          break;
        }
        case SEARCH_MODE.GLOBAL_SEARCH: {
          headerIcon = "search";
          headerTitle = `Search for '${searchTerm}'`;
          break;
        }
        case SEARCH_MODE.RECENT_ITEMS: {
          headerTitle = "Recent Items";
          headerIcon = "history";
          break;
        }
        default:
          break;
      }

      return {
        headerTitle,
        headerIcon,
        headerAction
      };
    }
  )
);

const AppSearchBar = ({
  className,
  pickerId,
  docRefs,
  thisNavigateToFolder,
  onKeyDownWithShortcuts,
  headerTitle,
  headerIcon,
  headerAction,
  hasNoResults,
  noResultsText,
  provideBreadcrumbs,
  onChange,
  valueToShow,
  onSearchFocus,
  onSearchBlur,
  onSearchTermChange
}) => (
  <div className={`dropdown ${className}`}>
    <input
      className="app-search-bar__input"
      icon="search"
      placeholder="Search..."
      value={valueToShow}
      onFocus={onSearchFocus}
      onBlur={onSearchBlur}
      onChange={onSearchTermChange}
    />
    <div
      tabIndex={0}
      onKeyDown={onKeyDownWithShortcuts}
      className="dropdown__content app-search-bar__dropdown-content"
    >
      <div className="app-search-header">
        <FontAwesomeIcon icon={headerIcon} size="lg" onClick={headerAction} />
        <div className="app-search-header__text">{headerTitle}</div>
        <ModeOptionButtons pickerId={pickerId} />
      </div>
      <div className="app-search-listing">
        {hasNoResults && (
          <div className="app-search-listing__empty">{noResultsText}</div>
        )}
        {docRefs.map(searchResult => (
          <DocRefListingEntry
            key={searchResult.uuid}
            listingId={pickerId}
            docRef={searchResult}
            openDocRef={onChange}
            enterFolder={thisNavigateToFolder}
          >
            {provideBreadcrumbs && (
              <DocRefBreadcrumb
                docRefUuid={searchResult.uuid}
                openDocRef={thisNavigateToFolder}
              />
            )}
          </DocRefListingEntry>
        ))}
      </div>
    </div>
  </div>
);

const EnhancedAppSearchBar = enhance(AppSearchBar);

EnhancedAppSearchBar.defaultProps = {
  pickerId: "global",
  typeFilters: [],
  onChange: () => console.log("onChange not provided for app search bar")
};

// This component is used to throw focus away from the dropdown when a value is picked
class AppSearchWithFocus extends React.Component {
  constructor(props) {
    super(props);

    this.dummyFocusRef = React.createRef();
  }
  render() {
    return (
      <React.Fragment>
        <span
          tabIndex={0}
          ref={this.dummyFocusRef}
          onFocus={() => this.dummyFocusRef.current.blur()}
        />
        <EnhancedAppSearchBar
          {...this.props}
          onChange={d => {
            this.dummyFocusRef.current.focus();
            this.props.onChange(d);
          }}
        />
      </React.Fragment>
    );
  }
}

export default AppSearchWithFocus;
