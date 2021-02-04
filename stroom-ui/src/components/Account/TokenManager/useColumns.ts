import { Token } from "../types";
import { useMemo } from "react";
import { Column } from "react-table";
import useDateUtil from "../../../lib/useDateUtil";

const useColumns = (): Column<Token>[] => {
  const { toDateString } = useDateUtil();
  return useMemo(
    () => [
      {
        Header: "",
        accessor: "id",
      },
      {
        Header: "User Id",
        accessor: "userId",
        maxWidth: 150,
      },
      {
        Header: "Status",
        accessor: ({ enabled }) => (enabled ? "Enabled" : "Disabled"),
        maxWidth: 100,
      },
      {
        Header: "Expires on",
        accessor: ({ expiresOnMs }) => expiresOnMs && toDateString(expiresOnMs),
        width: 165,
        maxWidth: 165,
      },
      {
        Header: "Issued on",
        accessor: ({ createTimeMs }) =>
          createTimeMs && toDateString(createTimeMs),
        width: 165,
        maxWidth: 165,
      },
    ],
    [toDateString],
  );
};

export default useColumns;
