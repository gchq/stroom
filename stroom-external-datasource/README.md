# stroom-external-datasource

The External Data Source document type: data from outside Stroom that StroomQL and dashboards can
query (gchq/stroom#6010). Its Type is JDBC or Amazon Athena, queried with the SQL in each query's `sql`
clause, or CSV or an Excel workbook held in the document, queried with Stroom's own clauses.

* [DESIGN.md](DESIGN.md) - the design and work plan.
* `StroomDB-ExternalDataSource-Examples.zip` - an import pack for trying it out in dev or test:
  * an External Data Source pointed at Stroom's own MySQL database, with example StroomQL queries. Its
    set-up steps are on the data source's Documentation tab after import;
  * a CSV External Data Source, Teams, and an Excel one, Bank Holidays, each with a query of it. They
    need no set-up.
