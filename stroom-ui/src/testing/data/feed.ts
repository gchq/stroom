import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { FeedDoc } from "components/DocumentEditors/useDocumentApi/types/feed";

export const generate = (): FeedDoc => ({
  type: "Feed",
  uuid: v4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
