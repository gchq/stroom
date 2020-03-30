# makefile for stroom-ui
#
# Use mmake instead of make: https://github.com/tj/mmake

# I.e. npm or yarn
BUILD_SYSTEM=yarn

# Build a local-SNAPSHOT docker image
snapshot:
	rm -rf build
	${BUILD_SYSTEM} run build
	cd docker && ./build.sh local-SNAPSHOT
.PHONY: snapshot

# Clean node_modules
clean:
	rm -rf node_modules
.PHONY: clean

# Clean package locks and node_modules
hard_clean: clean
	rm -f package-lock.json
	rm -f yarn.lock
.PHONY: hard_clean

# Clean package locks and node_modules and do an install
hard_install: hard_clean
	${BUILD_SYSTEM} install
.PHONY: hard_install

# Refresh node_modules
install: clean
	${BUILD_SYSTEM} install
.PHONY: install
