FROM jupyter/minimal-notebook
LABEL maintainer="Klaus Harbo <klaus@harbo.net>"

ARG NB_USER=jovyan
ENV NOTEBOOK_PATH $HOME/notebooks
ENV PORT 8888
ENV CLOJUPYTER_PATH $HOME/clojupyter
ENV LEIN_ROOT 1

USER root
RUN    	apt update \
	&& apt-get install -y curl openjdk-8-jdk \
	&& curl -o /usr/local/bin/lein https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein \
 	&& chmod +x /usr/local/bin/lein \
 	&& lein self-install
