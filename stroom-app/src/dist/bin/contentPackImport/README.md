# Automatic Content Pack Import

Stroom has a feature to automatically import any content packs found in this directory on startup. 
This feature can be used to pre-populate an instance with content, for example, when setting up a test environment.

This feature is enabled with the property `stroom.contentPackImportEnabled`.
If this is set to `true`, then any `zip` files found in the same directory as this README file will be treated as content packs and imported into Stroom during the startup process. Successfully imported packs will be moved to a sub-directory.
Similarly packs that could not be imported will also be moved to a sub-directory.
