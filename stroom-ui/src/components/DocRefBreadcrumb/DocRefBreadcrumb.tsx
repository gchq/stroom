import * as React from "react";
import { connect } from "react-redux";
import { compose, branch, renderComponent } from "recompose";

import withDocumentTree, {
  EnhancedProps as WithDocumentTreeProps
} from "../FolderExplorer/withDocumentTree";
import { findItem } from "../../lib/treeUtils";
import { DocRefConsumer, DocRefWithLineage } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";
import Loader from "../Loader";

export interface Props {
  docRefUuid: string;
  openDocRef: DocRefConsumer;
  className?: string;
}

interface ConnectState {
  docRefWithLineage: DocRefWithLineage;
}

interface ConnectDispatch {}

export interface EnhancedProps
  extends Props,
    WithDocumentTreeProps,
    ConnectState,
    ConnectDispatch {}

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
  branch(
    ({ docRefWithLineage }) => !docRefWithLineage || !docRefWithLineage.node,
    renderComponent<Props>(({ docRefUuid }) => (
      <Loader message={`Loading Doc Ref ${docRefUuid}...`} />
    ))
  )
);

const Divider = () => <div className="DocRefBreadcrumb__divider">/</div>;

const DocRefBreadcrumb = ({
  docRefWithLineage: {
    lineage,
    node: { name }
  },
  openDocRef,
  className = ""
}: EnhancedProps) => (
  <div className={`DocRefBreadcrumb ${className || ""}`}>
    {lineage.map(l => (
      <React.Fragment key={l.uuid}>
        <Divider />
        <a
          className="DocRefBreadcrumb__link"
          title={l.name}
          onClick={() => openDocRef(l)}
        >
          {l.name}
        </a>
      </React.Fragment>
    ))}
    <Divider />
    <div className="DocRefBreadcrumb__name">{name}</div>
  </div>
);

export default enhance(DocRefBreadcrumb);
