uberjar: update-version-edn
	lein uberjar

update-version-edn:
	lein update-version-edn

clean:
	lein clean

install: uberjar
	lein clojupyter-install

install-version: uberjar
	lein clojupyter-install --jupyter-kernel-dir :version

install-version-tag-icons: uberjar
	lein clojupyter-install --jupyter-kernel-dir :version --tag-icons

