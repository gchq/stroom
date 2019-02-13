import * as React from "react";
import { useEffect } from "react";
import { connect } from "react-redux";
import { compose } from "recompose";
import { withRouter, RouteComponentProps } from "react-router-dom";

import { fetchDocTree } from "../FolderExplorer/explorerClient";
import AppSearchBar from "../AppSearchBar";
import { DocRefIconHeader } from "../IconHeader";
import DocRefBreadcrumb from "../DocRefBreadcrumb";
import Button, { Props as ButtonProps } from "../Button";
import { DocRefWithLineage, DocRefConsumer } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";
import { findItem } from "../../lib/treeUtils";
import Loader from "../Loader";

export interface Props {
  actionBarItems: Array<ButtonProps>;
  docRefUuid: string;
  children?: React.ReactNode;
}

interface ConnectState {
  docRefWithLineage: DocRefWithLineage;
}

interface ConnectDispatch {
  fetchDocTree: typeof fetchDocTree;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    RouteComponentProps<any> {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { documentTree } }, { docRefUuid }) => ({
      docRefWithLineage: findItem(documentTree, docRefUuid) as DocRefWithLineage
    }),
    { fetchDocTree }
  ),
  withRouter
);

const DocRefEditor = ({
  docRefWithLineage,
  actionBarItems,
  children,
  fetchDocTree,
  history
}: EnhancedProps) => {
  useEffect(() => {
    fetchDocTree();
  });

  const openDocRef: DocRefConsumer = d =>
    history.push(`/s/doc/${d.type}/${d.uuid}`);

  if (!docRefWithLineage) {
    return <Loader message="Loading Doc Ref" />;
  }

  const { node } = docRefWithLineage;

  return (
    <div className="DocRefEditor">
      <AppSearchBar
        pickerId="doc-ref-editor-app-search"
        className="DocRefEditor__searchBar"
        onChange={openDocRef}
      />

      <DocRefIconHeader
        docRefType={node.type}
        className="DocRefEditor__header"
        text={node.name || "no name"}
      />

      <DocRefBreadcrumb
        className="DocRefEditor__breadcrumb"
        docRefUuid={node.uuid}
        openDocRef={openDocRef}
      />

      <div className="DocRefEditor__actionButtons">
        {actionBarItems.map((actionBarItem, i) => (
          <Button key={i} circular {...actionBarItem} />
        ))}
      </div>
      <div className="DocRefEditor__main">{children}</div>
    </div>
  );
};

export default enhance(DocRefEditor);
