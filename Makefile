.PHONY: clean metajar install docker-build conda-build docker-clean

VERSION = $(shell bin/version)
IDENT = clojupyter-$(VERSION)
docker-files = docker/Dockerfile docker/kernel/kernel.json docker/kernel/logo-64x64.png docker/lib/$(IDENT).jar
clojure-files = $(shell find src -iname '*.clj')
resource-files = $(shell find resources -type f)

metajar:
	lein metajar

clean: docker-clean
	lein clean

install: target/$(IDENT).jar bin/install
	bin/install

docker-build: $(docker-files)
	docker build --build-arg IDENT=$(IDENT) -t clojupyter:$(VERSION) docker


target/$(IDENT).jar: $(clojure-files) $(resource-files) project.clj
	lein metajar


docker/kernel:
	mkdir docker/kernel

docker/lib:
	mkdir docker/lib

docker/kernel/kernel.json: bin/kernel.json project.clj | docker/kernel
	bin/kernel.json /home/jovyan/.local/lib >docker/kernel/kernel.json

docker/kernel/logo-64x64.png: resources/clojupyter/assets/logo-64x64.png | docker/kernel
	cp resources/clojupyter/assets/logo-64x64.png docker/kernel

docker/lib/$(IDENT).jar: target/$(IDENT).jar | docker/lib
	cp -r target/$(IDENT).jar target/lib docker/lib

docker-clean:
	rm -rvf docker/*/

conda-build: uberjar
	@echo "BUILDNUM=${BUILDNUM}"
	lein clojupyter conda-build --buildnum "${BUILDNUM}"
