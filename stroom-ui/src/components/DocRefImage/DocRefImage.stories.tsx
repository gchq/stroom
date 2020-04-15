import * as React from "react";

import { storiesOf } from "@storybook/react";

import DocRefImage from "./DocRefImage";
/**
 * These are the names of the docRefTypes, but taken from the svg image names
 * in src/images/docRefTypes.
 *
 * You can re-create this list using the following bash:
 *   for i in `ls | sort | cut -d'.' -f1`; do echo "\"$i\","; done
 */
const docRefTypes = [
  "AnalyticOutputStore",
  "AnnotationsIndex",
  "Dashboard",
  "Dictionary",
  "ElasticIndex",
  "Feed",
  "Folder",
  "Index",
  "Pipeline",
  "RuleSet",
  "Script",
  "SelectAllOrNone",
  "StatisticStore",
  "StroomStatsStore",
  "System",
  "TextConverter",
  "Visualisation",
  "XMLSchema",
  "XSLT",
];

const allImages = (size?: "lg" | "sm") => (
  <table>
    <thead>
      <tr>
        <th>Icon</th>
        <th>docRefType</th>
      </tr>
    </thead>
    <tbody>
      {docRefTypes.map(docRefType => (
        <tr key={docRefType}>
          <td>
            <DocRefImage size={size} docRefType={docRefType} />
          </td>
          <td>
            <label>{docRefType}</label>
          </td>
        </tr>
      ))}
    </tbody>
  </table>
);

storiesOf("Doc Ref/Image", module)
  .add("default (large)", () => allImages())
  .add("small", () => allImages("sm"))
  .add("large", () => allImages("lg"));
