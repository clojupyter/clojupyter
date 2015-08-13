all:
	mkdir -p ~/.ipython/kernels/clojure
	rm -f target/*.jar
	lein uberjar
	cat bin/ipython-clojure.template target/*-standalone.jar > bin/ipython-clojure
	chmod +x bin/ipython-clojure
	cp bin/ipython-clojure ~/.ipython/kernels/clojure/ipython-clojure
	@if [ ! -f ~/.ipython/kernels/clojure/kernel.json ]; then\
		sed 's|HOME|'${HOME}'|' resources/kernel.json > ~/.ipython/kernels/clojure/kernel.json;\
	fi
