export interface ResourcesByDocType {
  XSLT: string;
  Pipeline: string;
  Index: string;
  Feed: string;
  Dictionary: string;
  AnnotationsIndex: string;
  ElasticIndex: string;
  Dashboard: string;
  Script: string;
  StatisticStore: string;
  StroomStatsStore: string;
  Visualisation: string;
  XMLSchema: string;
  Folder?: string;
}

export const DOCUMENT_RESOURCES: ResourcesByDocType = {
  XSLT: "/xslt/v1/",
  Pipeline: "/pipelines/v1/",
  Index: "/index/v1/",
  Feed: "/feed/v1/",
  Dictionary: "/dictionary/v1/",
  AnnotationsIndex: "/annotationsIndex/v1/",
  ElasticIndex: "/elasticIndex/v1/",
  Dashboard: "/dashboard/v1/",
  Script: "/script/v1/",
  StatisticStore: "/statistics/v1/",
  StroomStatsStore: "/stroomStats/v1/",
  Visualisation: "/visualisation/v1/",
  XMLSchema: "/xmlSchema/v1/",
};
