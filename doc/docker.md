# The Docker way

A Docker image  exists to make trying out Clojupyter easier.  To try it:

1. [Install Docker](https://docs.docker.com/engine/installation/) based on your platform.
2. Run `docker run -p 8888:8888 --rm simplect/clojupyter:0.2.2` to have clojupyter
   run on your machine.

The first time you start it Docker will pull the Docker image from `hub.docker.com`, which takes time.

More detailed introduction and usage guide is available on
[the home page of the clojupyter Docker image](https://github.com/klausharbo/clojupyter-docker).

