import * as React from "react";
import { useState, useEffect } from "react";

import { compose } from "recompose";
import { connect } from "react-redux";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import { findItem, filterTree } from "../../lib/treeUtils";
import { StoreState as AppSearchStoreState, SearchMode } from "./redux";
import { DocRefType, DocRefTree, DocRefWithLineage } from "../../types";
import { searchApp } from "../FolderExplorer/explorerClient";
import { DocRefBreadcrumb } from "../DocRefBreadcrumb";
import DocRefListingEntry from "../DocRefListingEntry";
import { fetchDocTree } from "../FolderExplorer/explorerClient";

import ModeOptionButtons from "./ModeOptionButtons";
import { GlobalStoreState } from "../../startup/reducers";
import { IconProp } from "@fortawesome/fontawesome-svg-core";
import useSelectableItemListing from "../../lib/useSelectableItemListing";

interface Props {
  pickerId: string;
  typeFilters?: Array<string>;
  onChange: (d: DocRefType) => any;
  value?: DocRefType;
  className?: string;
}

interface ConnectState {
  appSearch: AppSearchStoreState;
  documentTree: DocRefTree;
  recentItems: Array<DocRefType>;
}

interface ConnectDispatch {
  searchApp: typeof searchApp;
  fetchDocTree: typeof fetchDocTree;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ appSearch, recentItems, documentTree }) => ({
      appSearch,
      recentItems,
      documentTree
    }),
    {
      searchApp,
      fetchDocTree
    }
  )
);

const AppSearchBar = ({
  fetchDocTree,
  className,
  pickerId,
  typeFilters = [],
  onChange,
  appSearch,
  value,
  documentTree,
  recentItems,
  searchApp
}: EnhancedProps) => {
  let searchResults = appSearch[pickerId] || [];

  let [textFocus, setTextFocus] = useState<boolean>(false);
  let [searchTerm, setSearchTerm] = useState<string>("");
  let [searchMode, setSearchMode] = useState<SearchMode>(SearchMode.NAVIGATION);
  let [navFolder, setNavFolder] = useState<DocRefType | undefined>(undefined);

  const onSearchFocus = () => setTextFocus(true);
  const onSearchBlur = () => setTextFocus(false);

  const documentTreeToUse: DocRefTree =
    typeFilters.length > 0
      ? filterTree(documentTree, d => typeFilters.includes(d.type))!
      : documentTree;

  let docRefs: Array<DocRefType> = [];
  let thisFolder: DocRefTree | undefined = undefined;
  let parentFolder: DocRefType | undefined = undefined;
  let valueToShow: string;

  if (textFocus) {
    valueToShow = searchTerm;
  } else if (value) {
    valueToShow = value.name || "UNKNOWN_NAME";
  } else {
    valueToShow = "";
  }

  switch (searchMode) {
    case SearchMode.NAVIGATION: {
      const navFolderToUse = navFolder || documentTreeToUse;
      const navFolderWithLineage: DocRefWithLineage = findItem(
        documentTreeToUse,
        navFolderToUse.uuid
      )!;
      docRefs = navFolderWithLineage.node.children || [];
      thisFolder = navFolderWithLineage.node;

      if (
        navFolderWithLineage.lineage &&
        navFolderWithLineage.lineage.length > 0
      ) {
        parentFolder =
          navFolderWithLineage.lineage[navFolderWithLineage.lineage.length - 1];
      }
      break;
    }
    case SearchMode.GLOBAL_SEARCH: {
      docRefs = searchResults;
      break;
    }
    case SearchMode.RECENT_ITEMS: {
      docRefs = recentItems;
      break;
    }
    default:
      docRefs = [];
      break;
  }

  let hasNoResults = docRefs.length === 0;
  let noResultsText =
    searchMode === SearchMode.NAVIGATION ? "empty" : "no results";
  let provideBreadcrumbs = searchMode !== SearchMode.NAVIGATION;

  const onThisChange = (docRef: DocRefType) => {
    if (docRef.type === "Folder") {
      if (
        !typeFilters ||
        typeFilters.length === 0 ||
        typeFilters.includes("Folder")
      ) {
        onChange(docRef);
      } else {
        setNavFolder(docRef);
      }
    } else {
      onChange(docRef);
    }
  };

  const {
    onKeyDownWithShortcuts,
    selectionToggled,
    selectedItems: selectedDocRefs,
    focussedItem: focussedDocRef
  } = useSelectableItemListing({
    items: docRefs,
    openItem: onThisChange,
    getKey: d => d.uuid,
    enterItem: d => setNavFolder(d),
    goBack: () => {
      if (!!parentFolder) {
        setNavFolder(parentFolder);
      }
    }
  });

  useEffect(() => {
    fetchDocTree();
  });

  let headerTitle = "unknown";
  let headerIcon: IconProp = "cross";
  let headerAction = () => {};

  switch (searchMode) {
    case SearchMode.NAVIGATION: {
      if (!!thisFolder) {
        headerTitle = thisFolder.name || "no name";
        if (!!parentFolder) {
          headerIcon = "arrow-left";
          headerAction = () => setNavFolder(parentFolder!);
        } else {
          headerIcon = "folder";
        }
      }
      break;
    }
    case SearchMode.GLOBAL_SEARCH: {
      headerIcon = "search";
      headerTitle = `Search for '${searchTerm}'`;
      break;
    }
    case SearchMode.RECENT_ITEMS: {
      headerTitle = "Recent Items";
      headerIcon = "history";
      break;
    }
    default:
      break;
  }

  const onSearchTermChange: React.ChangeEventHandler<HTMLInputElement> = ({
    target: { value }
  }) => {
    setSearchTerm(value);
    setSearchMode(
      value.length > 0 ? SearchMode.GLOBAL_SEARCH : SearchMode.NAVIGATION
    );
    searchApp(pickerId, { term: value });
  };

  return (
    <div className={`dropdown app-search-bar ${className || ""}`}>
      <input
        className="app-search-bar__input"
        //icon="search" // TODO
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
          <div onClick={headerAction}>
            <FontAwesomeIcon icon={headerIcon} size="lg" />
          </div>
          <div className="app-search-header__text">{headerTitle}</div>
          <ModeOptionButtons switchMode={setSearchMode} />
        </div>
        <div className="app-search-listing">
          {hasNoResults && (
            <div className="app-search-listing__empty">{noResultsText}</div>
          )}
          {docRefs.map(searchResult => (
            <DocRefListingEntry
              key={searchResult.uuid}
              docRef={searchResult}
              openDocRef={onThisChange}
              enterFolder={setNavFolder}
              selectionToggled={selectionToggled}
              selectedDocRefs={selectedDocRefs}
              focussedDocRef={focussedDocRef}
            >
              {provideBreadcrumbs && (
                <DocRefBreadcrumb
                  docRefUuid={searchResult.uuid}
                  openDocRef={setNavFolder}
                />
              )}
            </DocRefListingEntry>
          ))}
        </div>
      </div>
    </div>
  );
};

export default enhance(AppSearchBar);

// This component is used to throw focus away from the dropdown when a value is picked
// class AppSearchWithFocus extends React.Component<Props> {
//   constructor(props) {
//     super(props);

//     this.dummyFocusRef = React.createRef();
//   }
//   render() {
//     return (
//       <React.Fragment>
//         <span
//           tabIndex={0}
//           ref={this.dummyFocusRef}
//           onFocus={() => this.dummyFocusRef.current.blur()}
//         />
//         <EnhancedAppSearchBar
//           {...this.props}
//           onChange={d => {
//             this.dummyFocusRef.current.focus();
//             this.props.onChange(d);
//           }}
//         />
//       </React.Fragment>
//     );
//   }
// }

// export default AppSearchWithFocus;
// TODO Have another crack at this later
