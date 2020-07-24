import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { AnnotationsIndexDoc } from "components/DocumentEditors/useDocumentApi/types/annotations";

export const generate = (): AnnotationsIndexDoc => ({
  type: "AnnotationsIndex",
  uuid: v4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
