# Clojupyter Docker image

In directory is a `Dockerfile` for [clojupyter](https://github.com/clojupyter/clojupyter) that has
previously been used successfully to build a Docker image containing Clojupyter.

## Building the image

To build an image of e.g. Clojupyter version `0.2.2` :

```sh
CLOJUPYTER_VERSION=0.2.2 make
```

A release of Clojupyter with the name assigned to `CLOJUPYTER_VERSION` must exist on Clojars.
