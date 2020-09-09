clean:
	lein clean

update-version-edn:
	lein update-version-edn

uberjar:
	lein uberjar

metajar:
	lein metajar

install: metajar
	bin/install

conda-build: uberjar
	@echo "BUILDNUM=${BUILDNUM}"
	lein clojupyter conda-build --buildnum "${BUILDNUM}"
