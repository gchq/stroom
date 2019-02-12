import * as React from "react";
import { useEffect } from "react";
import { connect } from "react-redux";
import { compose } from "recompose";

import { findItem } from "../../lib/treeUtils";
import { DocRefConsumer, DocRefWithLineage } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";
import Loader from "../Loader";
import { fetchDocTree } from "../FolderExplorer/explorerClient";

export interface Props {
  docRefUuid: string;
  openDocRef: DocRefConsumer;
  className?: string;
}

interface ConnectState {
  docRefWithLineage: DocRefWithLineage;
}

interface ConnectDispatch {
  fetchDocTree: typeof fetchDocTree;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { documentTree } }, { docRefUuid }) => ({
      docRefWithLineage: findItem(documentTree, docRefUuid) as DocRefWithLineage
    }),
    { fetchDocTree }
  )
);

const Divider = () => <div className="DocRefBreadcrumb__divider">/</div>;

const DocRefBreadcrumb = ({
  docRefUuid,
  docRefWithLineage,
  openDocRef,
  fetchDocTree,
  className = ""
}: EnhancedProps) => {
  useEffect(() => {
    fetchDocTree();
  });

  if (!docRefWithLineage || !docRefWithLineage.node) {
    return <Loader message={`Loading Doc Ref ${docRefUuid}...`} />;
  }

  const {
    lineage,
    node: { name }
  } = docRefWithLineage;

  return (
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
};

export default enhance(DocRefBreadcrumb);
