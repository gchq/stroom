import * as React from "react";
import { connect } from "react-redux";
import { compose, lifecycle } from "recompose";

import { findItem } from "../../lib/treeUtils";
import FolderExplorer from "../FolderExplorer";
import DictionaryEditor from "../DictionaryEditor";
import PipelineEditor from "../PipelineEditor";
import XsltEditor from "../XsltEditor";
import PathNotFound from "../PathNotFound";
import { actionCreators } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";
import { DocRefType, DocRefTree } from "../../types";

const { docRefOpened } = actionCreators;

export interface Props {
  docRef: DocRefType;
}
interface ConnectState {
  documentTree: DocRefTree;
}
interface ConnectDispatch {
  docRefOpened: typeof docRefOpened;
}
export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { documentTree } }) => ({ documentTree }),
    { docRefOpened }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const {
        documentTree,
        docRefOpened,
        docRef: { uuid }
      } = this.props;

      const openedDocRefWithLineage = findItem(documentTree, uuid);

      if (openedDocRefWithLineage) {
        docRefOpened(openedDocRefWithLineage.node);
      }
    }
  })
);

let SwitchedDocRefEditor = ({ docRef: { type, uuid } }: EnhancedProps) => {
  switch (type) {
    case "System":
    case "Folder":
      return <FolderExplorer folderUuid={uuid} />;
    case "AnnotationsIndex":
      return <div>Annotations Index Editor</div>;
    case "ElasticIndex":
      return <div>Elastic Index Editor</div>;
    case "XSLT":
      return <XsltEditor xsltUuid={uuid} />;
    case "Pipeline":
      return <PipelineEditor pipelineId={uuid} />;
    case "Dashboard":
      return <div>Dashboard Editor</div>;
    case "Dictionary":
      return (
        <DictionaryEditor dictionaryUuid={uuid}>
          Dictionary Editor
        </DictionaryEditor>
      );
    case "Feed":
      return <div>Feed Editor</div>;
    case "Index":
      return <div>Index Editor</div>;
    case "Script":
      return <div>Script Editor</div>;
    case "StatisticStore":
      return <div>Statistics Store Editor</div>;
    case "StroomStatsStore":
      return <div>Stroom Stats Store Editor</div>;
    case "TextConverter":
      return <div>Text Converter Editor</div>;
    case "Visualisation":
      return <div>Visualisation Editor</div>;
    case "XMLSchema":
      return <div>XML Schema Editor</div>;
    default:
      return (
        <PathNotFound message="no editor provided for this doc ref type " />
      );
  }
};

export default enhance(SwitchedDocRefEditor);
