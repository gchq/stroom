import * as React from "react";
import Select, { components } from "react-select";
import useDocumentSearch from "components/DocumentEditors/api/explorer/useDocumentSearch";
import {
  DocRefType,
  DocRefTree,
  DocRefWithLineage,
  copyDocRef,
} from "components/DocumentEditors/useDocumentApi/types/base";
import { OptionProps, SingleValueProps } from "react-select";
import DocRefImage from "../DocRefImage";
import ModeOptionButtons, { SearchMode } from "./ModeOptionButton";
import { useModeOptionButtons } from "./ModeOptionButton/ModeOptionButtons";
import { filterTree, findItem } from "lib/treeUtils/treeUtils";
import useRecentItems from "lib/useRecentItems";
import { KeyboardEventHandler, MenuProps } from "react-select";
import { IconProp } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import DocRefBreadcrumb from "components/DocRefBreadcrumb";

export interface Props {
  typeFilter?: string;
  onChange: (d: DocRefType) => any;
  value?: DocRefType;
  className?: string;
}

const getDocRefValue = (d: DocRefType) => d.uuid;
const getDocRefLabel = (d: DocRefType) => d.name || `${d.type} - ${d.uuid}`;

const SingleValue: React.FunctionComponent<SingleValueProps<DocRefType>> = ({
  children,
  data: { type },
}) => (
  <div className="DocRefTypePicker">
    <DocRefImage
      className="DocRefTypePicker__image"
      size="lg"
      docRefType={type}
    />
    <div className="DocRefTypePicker__text">{children}</div>
  </div>
);

const Option: React.FunctionComponent<OptionProps<DocRefType>> = (
  props: OptionProps<DocRefType>,
) => {
  const { onOptionFocus, provideBreadcrumbs } = props.selectProps;

  // We are using the change to isFocused to true to detect 'onFocus'
  React.useEffect(() => {
    if (props.isFocused) {
      onOptionFocus(props.data);
    }
  }, [props.isFocused, props.data, onOptionFocus]);
  return (
    <components.Option {...props}>
      <div className="DocRefTypePicker">
        <DocRefImage
          className="DocRefTypePicker__image"
          size="lg"
          docRefType={props.data.type}
        />
        <div className="DocRefTypePicker__text">{props.children}</div>
      </div>
      {provideBreadcrumbs && (
        <DocRefBreadcrumb
          docRefUuid={props.data.uuid}
          openDocRef={onOptionFocus}
        />
      )}
    </components.Option>
  );
};

const DropdownIndicator: React.FunctionComponent<any> = (props) => {
  return <ModeOptionButtons {...props.selectProps.modeOptionProps} />;
};

const Menu: React.FunctionComponent<MenuProps<DocRefType>> = (props) => {
  const { headerIcon, headerTitle } = props.selectProps;
  return (
    <React.Fragment>
      <components.Menu {...props}>
        <React.Fragment>
          <div className="AppSearchBar__header">
            <div>
              <FontAwesomeIcon icon={headerIcon} size="lg" />
            </div>
            <div className="AppSearchBar__header-text">{headerTitle}</div>
          </div>
          {props.children}
        </React.Fragment>
      </components.Menu>
    </React.Fragment>
  );
};

const AppSearchBar: React.FunctionComponent<Props> = ({
  typeFilter,
  onChange: onChangeRaw,
  value,
  className,
}) => {
  const onChange: (d: DocRefType) => void = React.useCallback(
    (d) => onChangeRaw(copyDocRef(d)),
    [onChangeRaw],
  );
  const [searchTerm, setSearchTerm] = React.useState<string>("");
  const { documentTree, searchResults, searchApp } = useDocumentSearch();
  const [highlightedDocRef, onOptionFocus] = React.useState<
    DocRefType | undefined
  >(undefined);
  const { recentItems: recentItemsAll } = useRecentItems();
  const recentItems: DocRefType[] = React.useMemo(
    () =>
      !!typeFilter
        ? recentItemsAll.filter((r) => r.type === typeFilter)
        : recentItemsAll,
    [recentItemsAll, typeFilter],
  );
  const {
    searchMode,
    componentProps: modeOptionProps,
  } = useModeOptionButtons();
  const { switchMode } = modeOptionProps;
  const [navFolder, setNavFolder] = React.useState<DocRefType | undefined>(
    undefined,
  );

  const onSearchTermChange = React.useCallback(
    (newValue: string) => {
      switchMode(
        newValue.length > 0 ? SearchMode.GLOBAL_SEARCH : SearchMode.NAVIGATION,
      );
      searchApp({ term: newValue, docRefType: typeFilter });
      setSearchTerm(newValue);
    },
    [typeFilter, searchApp, switchMode, setSearchTerm],
  );

  const onThisChange = React.useCallback(
    (docRef: DocRefType) => {
      if (docRef.type === "Folder") {
        if (!typeFilter || typeFilter === "Folder") {
          onChange(docRef);
        } else {
          setNavFolder(docRef);
        }
      } else {
        onChange(docRef);
      }
    },
    [typeFilter, onChange, setNavFolder],
  );

  const documentTreeToUse: DocRefTree = React.useMemo(() => {
    return typeFilter !== undefined
      ? filterTree(documentTree, (d) => typeFilter === d.type)!
      : documentTree;
  }, [typeFilter, documentTree]);
  const navFolderToUse = navFolder || documentTreeToUse;
  let docRefs: DocRefType[] = [];
  let parentFolder: DocRefType | undefined = undefined;
  const provideBreadcrumbs = searchMode !== SearchMode.NAVIGATION;

  switch (searchMode) {
    case SearchMode.NAVIGATION: {
      if (!!navFolderToUse) {
        const navFolderWithLineage: DocRefWithLineage = findItem(
          documentTreeToUse,
          navFolderToUse.uuid,
        )!;
        docRefs = navFolderWithLineage.node.children || [];

        if (
          navFolderWithLineage.lineage &&
          navFolderWithLineage.lineage.length > 0
        ) {
          parentFolder =
            navFolderWithLineage.lineage[
              navFolderWithLineage.lineage.length - 1
            ];
        }
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

  let headerTitle: string | React.ReactElement = "unknown";
  let headerIcon: IconProp = "cross";

  switch (searchMode) {
    case SearchMode.NAVIGATION: {
      headerTitle = !!navFolderToUse && (
        <DocRefBreadcrumb
          docRefUuid={navFolderToUse.uuid}
          openDocRef={onOptionFocus}
        />
      );
      if (!!parentFolder) {
        headerIcon = "arrow-left";
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

  const onKeyDown: KeyboardEventHandler = React.useCallback(
    (e) => {
      if (e.key === "ArrowRight" || e.key === "l") {
        if (!!highlightedDocRef && highlightedDocRef.type === "Folder") {
          setNavFolder(highlightedDocRef);
        }
      } else if (e.key === "ArrowLeft" || e.key === "h") {
        if (!!parentFolder) {
          setNavFolder(parentFolder);
        }
      }
    },
    [setNavFolder, parentFolder, highlightedDocRef],
  );

  return (
    <Select
      className={className}
      classNamePrefix={className}
      {...{
        onOptionFocus,
        modeOptionProps,
        headerTitle,
        headerIcon,
        provideBreadcrumbs,
      }} // passed through as selectProps
      onKeyDown={onKeyDown}
      options={docRefs}
      value={value}
      onChange={onThisChange}
      components={{ Menu, DropdownIndicator, SingleValue, Option }}
      onInputChange={onSearchTermChange}
      getOptionValue={getDocRefValue}
      getOptionLabel={getDocRefLabel}
    />
  );
};

export default AppSearchBar;
