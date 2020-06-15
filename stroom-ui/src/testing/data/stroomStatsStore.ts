import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { StroomStatsStoreDoc } from "components/DocumentEditors/useDocumentApi/types/statistics";

export const generate = (): StroomStatsStoreDoc => ({
  type: "StroomStatsStore",
  uuid: v4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
