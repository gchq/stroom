import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";
import {
  IndexField,
  IndexDoc,
} from "components/DocumentEditors/useDocumentApi/types/indexDoc";

export const generateTestField = (): IndexField => ({
  fieldName: loremIpsum({ count: 2, units: "words" }),
  fieldType: "ID",
  stored: true,
  indexed: true,
  termPositions: false,
  analyzerType: "KEYWORD",
  caseSensitive: false,
});

export const generate = (): IndexDoc => ({
  type: "Index",
  uuid: v4(),
  name: loremIpsum({ count: 2, units: "words" }),
  fields: [
    {
      fieldName: loremIpsum({ count: 2, units: "words" }),
      fieldType: "ID",
      stored: true,
      indexed: true,
      termPositions: false,
      analyzerType: "KEYWORD",
      caseSensitive: false,
    },
    {
      fieldName: loremIpsum({ count: 2, units: "words" }),
      fieldType: "FIELD",
      stored: true,
      indexed: true,
      termPositions: false,
      analyzerType: "KEYWORD",
      caseSensitive: false,
    },
    {
      fieldName: loremIpsum({ count: 2, units: "words" }),
      fieldType: "DATE_FIELD",
      stored: true,
      indexed: true,
      termPositions: false,
      analyzerType: "KEYWORD",
      caseSensitive: false,
    },
    {
      fieldName: loremIpsum({ count: 2, units: "words" }),
      fieldType: "NUMERIC_FIELD",
      stored: true,
      indexed: true,
      termPositions: false,
      analyzerType: "KEYWORD",
      caseSensitive: false,
    },
    {
      fieldName: loremIpsum({ count: 2, units: "words" }),
      fieldType: "FIELD",
      stored: true,
      indexed: true,
      termPositions: true,
      analyzerType: "ALPHA_NUMERIC",
      caseSensitive: false,
    },
    {
      fieldName: loremIpsum({ count: 2, units: "words" }),
      fieldType: "FIELD",
      stored: true,
      indexed: true,
      termPositions: true,
      analyzerType: "ALPHA_NUMERIC",
      caseSensitive: true,
    },
  ],
});
