import { DocumentBase } from "./base";

export type FeedStatus = "Receive" | "Reject" | "Drop";

export interface FeedDoc extends DocumentBase<"Feed"> {
  description?: string;
  classification?: string;
  encoding?: string;
  contextEncoding?: string;
  retentionDayAge?: number;
  reference?: boolean;
  streamType?: string;
  feedStatus?: FeedStatus;
}
