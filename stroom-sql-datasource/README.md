# stroom-sql-datasource

The SQL Data Source document type: a JDBC or Amazon Athena connection that StroomQL and dashboards
can query, with the SQL carried in each query's `sql` clause (gchq/stroom#6010).

* [DESIGN.md](DESIGN.md) - the design and work plan.
* `StroomDB-SqlDataSource-Examples.zip` - an import pack for trying it out in dev or test: a SQL Data
  Source pointed at Stroom's own MySQL database, with example StroomQL queries. Its set-up steps are
  on the data source's Documentation tab after import.
