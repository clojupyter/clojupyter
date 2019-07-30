clean:
	lein clean

update-version-edn:
	lein update-version-edn

uberjar: update-version-edn
	lein uberjar

install: uberjar
	lein clojupyter install

conda-build: uberjar
	@echo "BUILDNUM=${BUILDNUM}"
	lein clojupyter conda-build --buildnum "${BUILDNUM}"
