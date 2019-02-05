import * as React from "react";
import { connect } from "react-redux";
import { compose, withHandlers } from "recompose";
import { withRouter, RouteComponentProps } from "react-router-dom";

import withDocumentTree, {
  EnhancedProps as WithDocumentTreeProps
} from "../FolderExplorer/withDocumentTree";

import AppSearchBar from "../AppSearchBar";
import { DocRefIconHeader } from "../IconHeader";
import DocRefBreadcrumb from "../DocRefBreadcrumb";
import Button, { Props as ButtonProps } from "../Button";
import { DocRefConsumer, DocRefWithLineage } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";
import { findItem } from "../../lib/treeUtils";

export interface Props {
  actionBarItems: Array<ButtonProps>;
  docRefUuid: string;
  children?: React.ReactNode;
}

interface ConnectState {
  docRefWithLineage: DocRefWithLineage;
}

interface ConnectDispatch {}

interface WithHandlers {
  openDocRef: DocRefConsumer;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    RouteComponentProps<any>,
    WithHandlers {}

const enhance = compose<EnhancedProps, Props>(
  withDocumentTree,
  connect<
    ConnectState,
    ConnectDispatch,
    Props & WithDocumentTreeProps,
    GlobalStoreState
  >(
    ({}, { docRefUuid, documentTree }) => ({
      docRefWithLineage: findItem(documentTree, docRefUuid) as DocRefWithLineage
    }),
    {}
  ),
  withRouter,
  withHandlers<Props & RouteComponentProps<any>, WithHandlers>({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`)
  })
);

const DocRefEditor = ({
  docRefWithLineage: { node },
  openDocRef,
  actionBarItems,
  children
}: EnhancedProps) => (
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

export default enhance(DocRefEditor);
