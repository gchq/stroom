import { SwitchedDocRefEditorProps } from "../DocRefEditor/types";
import FolderExplorer from "../FolderExplorer";
import XsltEditor from "../XsltEditor";
import DictionaryEditor from "../DictionaryEditor";
import PipelineEditor from "../PipelineEditor";
import IndexEditor from "../IndexEditor";
import AnnotationsIndexEditor from "../AnnotationsIndexEditor";
import ElasticIndexEditor from "../ElasticIndexEditor";
import DashboardEditor from "../DashboardEditor";
import ScriptEditor from "../ScriptEditor";
import StroomStatsStoreEditor from "../StroomStatsStoreEditor";
import StatisticStoreEditor from "../StatisticStoreEditor";
import VisualisationEditor from "../VisualisationEditor";
import XMLSchemaEditor from "../XMLSchemaEditor";
import FeedEditor from "../FeedEditor";

export interface DocRefEditorClasses {
  [docRefType: string]: React.FunctionComponent<SwitchedDocRefEditorProps>;
}

export const docRefEditorClasses: DocRefEditorClasses = {
  AnnotationsIndex: AnnotationsIndexEditor,
  Dashboard: DashboardEditor,
  Dictionary: DictionaryEditor,
  ElasticIndex: ElasticIndexEditor,
  Feed: FeedEditor,
  Folder: FolderExplorer,
  Index: IndexEditor,
  Pipeline: PipelineEditor,
  Script: ScriptEditor,
  StatisticStore: StatisticStoreEditor,
  StroomStatsStore: StroomStatsStoreEditor,
  System: FolderExplorer,
  Visualisation: VisualisationEditor,
  XMLSchema: XMLSchemaEditor,
  XSLT: XsltEditor,
};
