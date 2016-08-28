all:
	lein uberjar
	cat bin/clojupyter.template $$(find . -maxdepth 2 -type f | grep -e ".*standalone.*\.jar") > bin/clojupyter
	chmod +x bin/clojupyter

clean:
	rm -f *.jar
	rm -f target/*.jar
	rm -f bin/clojuypyter

install:
	mkdir -p ~/.ipython/kernels/clojure
	cp bin/clojupyter ~/.ipython/kernels/clojure/clojupyter
	@if [ ! -f ~/.ipython/kernels/clojure/kernel.json ]; then\
		sed 's|HOME|'${HOME}'|' resources/kernel.json > ~/.ipython/kernels/clojure/kernel.json;\
	fi
