uberjar: update-version-edn
	lein uberjar

update-version-edn:
	lein update-version-edn

clean:
	lein clean

install: uberjar
	lein clojupyter-install

install-version: uberjar
	lein clojupyter-install --kernel-relpath :version

#eof
