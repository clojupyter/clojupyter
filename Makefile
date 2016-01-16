all:
	lein uberjar
	cat bin/ipython-clojure.template $$(find . -maxdepth 2 -type f | grep -e ".*standalone.*\.jar") > bin/ipython-clojure
	chmod +x bin/ipython-clojure

clean:
	rm -f *.jar
	rm -f target/*.jar
	rm -f bin/ipython-clojure

install:
	mkdir -p ~/.ipython/kernels/clojure
	cp bin/ipython-clojure ~/.ipython/kernels/clojure/ipython-clojure
	@if [ ! -f ~/.ipython/kernels/clojure/kernel.json ]; then\
		sed 's|HOME|'${HOME}'|' resources/kernel.json > ~/.ipython/kernels/clojure/kernel.json;\
	fi
