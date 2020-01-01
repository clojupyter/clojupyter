export CLOJUPYTER_VERSION

all: clojupyter-image

clojupyter-base-image:
	(cd clojupyter-base && ./build.sh)


clojupyter-image: clojupyter-base-image
	(cd clojupyter &&  ./build.sh)
