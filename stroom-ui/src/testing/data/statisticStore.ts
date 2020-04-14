import * as uuidv4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { StatisticStoreDoc } from "components/DocumentEditors/useDocumentApi/types/statistics";

export const generate = (): StatisticStoreDoc => ({
  type: "StatisticStore",
  uuid: uuidv4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
