import { ApiKey } from "api/stroom";
import { useMemo } from "react";
import { Column } from "react-table";
import useDateUtil from "../../../lib/useDateUtil";

const useColumns = (): Column<ApiKey>[] => {
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
        width: 190,
        maxWidth: 250,
      },
      {
        id: "enabled",
        Header: "Status",
        accessor: ({ enabled }) => (enabled ? "Enabled" : "Disabled"),
        maxWidth: 100,
      },
      {
        id: "expiresOnMs",
        Header: "Expires on",
        accessor: ({ expiresOnMs }) => expiresOnMs && toDateString(expiresOnMs),
        width: 165,
        maxWidth: 165,
      },
      {
        id: "createTimeMs",
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
