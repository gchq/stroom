# Auth into stroom migration

Lots to do, listed (perhaps) in order of work:

- [x] Get IDE to compile code
- [x] Add Guide module
- [x] Add resources into Stroom's DW config
- [x] Fix guice config
- [x] Merge flyway files
- [ ] Merge gradle build files, e.g. version numbers. What if we're using different versions of things? Migrate those too.
- [x] Ensure remaining files are copied accross, e.g. docker.sh and ?
- [x] Add auth config to stroom config
  - [x] Update config templates
  - [ ] Add config to docker yml 
- [ ] Rename/re-organise modules according to stroom standardresources
- [ ] Re-organise tiers according to stroom standard -- i.e. service for security, etc...
- [x] Get travis build working -- it'll be building multiple images now.
  - It won't be building multipe images
- [ ] Re-factor persistence so it doesn't use swagger? Stroom depends on this so we might want to do it later, but until we don't have swagger the build for auth will have a different process and we want it 100% aligned with Stroom.
- [ ] Make migration scripts re-runnable
- [ ] Restore DB tests
- [ ] Restore ITs
