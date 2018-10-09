import * as React from "react";

import { compose, withHandlers, withProps, withStateHandlers } from "recompose";
import { connect } from "react-redux";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import { findItem, filterTree } from "../../lib/treeUtils";
import {
  actionCreators as appSearchBarActionCreators,
  defaultStatePerId as appSearchDefaultStatePerId,
  SearchMode
} from "./redux";
import {
  DocRefType,
  DocRefConsumer,
  DocRefTree,
  DocRefWithLineage
} from "../../types";
import { searchApp } from "../FolderExplorer/explorerClient";
import { DocRefBreadcrumb } from "../DocRefBreadcrumb";
import DocRefListingEntry from "../DocRefListingEntry";
import withSelectableItemListing, {
  EnhancedProps as SelectableItemListingProps,
  defaultStatePerId as selectableItemListingDefaultStatePerId,
  StoreStatePerId as SelectableItemListingState
} from "../../lib/withSelectableItemListing";
import withDocumentTree, {
  EnhancedProps as WithDocumentTreeProps
} from "../FolderExplorer/withDocumentTree";

import ModeOptionButtons from "./ModeOptionButtons";
import { GlobalStoreState } from "../../startup/reducers";
import { IconProp } from "@fortawesome/fontawesome-svg-core";

const { searchTermUpdated, navigateToFolder } = appSearchBarActionCreators;

interface FocusStateProps {
  textFocus: boolean;
}

interface FocusHandlerProps {
  onSearchFocus: (a: any) => any;
  onSearchBlur: (a: any) => any;
}

interface Props {
  pickerId: string;
  typeFilters?: Array<string>;
  onChange: (d: DocRefType) => any;
  value?: DocRefType;
  className?: string;
}

interface ConnectState {
  searchTerm: string;
  searchMode: SearchMode;
  valueToShow: string;
  selectableItemListing: SelectableItemListingState;
  docRefs: Array<DocRefType>;
  hasNoResults: boolean;
  noResultsText: string;
  provideBreadcrumbs: boolean;
  thisFolder?: DocRefTree;
  parentFolder?: DocRefType;
}

interface ConnectDispatch {
  searchApp: typeof searchApp;
  searchTermUpdated: typeof searchTermUpdated;
  navigateToFolder: typeof navigateToFolder;
}

interface WithHandlers1 {
  onThisChange: DocRefConsumer;
}

interface WithHandlers2 {
  onSearchTermChange: React.ChangeEventHandler<HTMLInputElement>;
  thisNavigateToFolder: DocRefConsumer;
}

interface WithProps {
  headerTitle: string;
  headerIcon: IconProp;
  headerAction: () => any;
}

export interface EnhancedProps
  extends Props,
    WithDocumentTreeProps,
    FocusStateProps,
    FocusHandlerProps,
    ConnectState,
    ConnectDispatch,
    WithHandlers1,
    SelectableItemListingProps<DocRefType>,
    WithHandlers2,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  withDocumentTree,
  withStateHandlers(
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
  connect<
    ConnectState,
    ConnectDispatch,
    Props & FocusStateProps & FocusHandlerProps,
    GlobalStoreState
  >(
    (
      {
        appSearch,
        recentItems,
        selectableItemListings,
        folderExplorer: { documentTree }
      },
      { pickerId, typeFilters = [], value, textFocus }
    ) => {
      const appSearchForPicker =
        appSearch[pickerId] || appSearchDefaultStatePerId;
      const selectableItemListing =
        selectableItemListings[pickerId] ||
        selectableItemListingDefaultStatePerId;
      const {
        searchTerm,
        navFolder,
        searchResults,
        searchMode
      } = appSearchForPicker;
      const documentTreeToUse: DocRefTree =
        typeFilters.length > 0
          ? filterTree(documentTree, d => typeFilters.includes(d.type))!
          : documentTree;

      let docRefs: Array<DocRefType> = [];
      let thisFolder: DocRefTree | undefined = undefined;
      let parentFolder: DocRefType | undefined;
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
          docRefs = navFolderWithLineage.node.children!;
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

      return {
        searchTerm,
        searchMode,
        valueToShow,
        selectableItemListing,
        docRefs,
        hasNoResults: docRefs.length === 0,
        noResultsText:
          searchMode === SearchMode.NAVIGATION ? "empty" : "no results",
        provideBreadcrumbs: searchMode !== SearchMode.NAVIGATION,
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
    onThisChange: ({ onChange, typeFilters, pickerId, navigateToFolder }) => (
      docRef: DocRefType
    ) => {
      if (docRef.type === "Folder") {
        if (
          !typeFilters ||
          typeFilters.length === 0 ||
          typeFilters.includes("Folder")
        ) {
          onChange(docRef);
        } else {
          navigateToFolder(pickerId, docRef);
        }
      } else {
        onChange(docRef);
      }
    }
  }),
  withSelectableItemListing<DocRefType>(
    ({ pickerId, docRefs, navigateToFolder, parentFolder, onThisChange }) => ({
      listingId: pickerId,
      items: docRefs,
      openItem: onThisChange,
      getKey: d => d.uuid,
      enterItem: d => navigateToFolder(pickerId, d),
      goBack: () => navigateToFolder(pickerId, parentFolder)
    })
  ),
  withHandlers<
    Props & ConnectState & ConnectDispatch & WithHandlers1,
    WithHandlers2
  >({
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
        case SearchMode.NAVIGATION: {
          headerTitle = thisFolder.name;
          if (parentFolder) {
            headerIcon = "arrow-left";
            headerAction = () => thisNavigateToFolder(parentFolder);
          } else {
            headerIcon = "folder";
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
  onThisChange,
  valueToShow,
  onSearchFocus,
  onSearchBlur,
  onSearchTermChange
}: EnhancedProps) => (
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
            openDocRef={onThisChange}
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
