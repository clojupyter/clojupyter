all:
	lein uberjar

uberjar: all

clean:
	lein clean

install: uberjar
	lein clojupyter-install

#eof
