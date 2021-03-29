import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import { VisualisationDoc } from "components/DocumentEditors/useDocumentApi/types/visualisation";

export const generate = (): VisualisationDoc => ({
  type: "Visualisation",
  uuid: v4(),
  name: loremIpsum({ count: 2, units: "words" }),
});
