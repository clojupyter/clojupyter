all:
	rm -f target/*.jar
	lein uberjar
	cat bin/ipython-clojure.template target/*-standalone.jar > bin/ipython-clojure
	chmod +x bin/ipython-clojure
