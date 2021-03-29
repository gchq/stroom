import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { TextConverterDoc } from "components/DocumentEditors/useDocumentApi/types/textConverter";

export const generate = (): TextConverterDoc => ({
  type: "TextConverter",
  uuid: v4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
