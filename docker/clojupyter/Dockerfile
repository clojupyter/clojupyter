FROM clojupyter-base
LABEL maintainer="Klaus Harbo <klaus@harbo.net>"

ARG NB_USER=jovyan

ENV NOTEBOOK_PATH $HOME/notebooks
ENV PORT 8888
ENV LEIN_ROOT 1
ENV CLOJUPYTER_PATH $HOME/clojupyter-$CLOJUPYTER_VERSION

USER $NB_USER
RUN	mkdir -p $NOTEBOOK_PATH \
	&& git clone https://github.com/clojupyter/clojupyter $CLOJUPYTER_PATH

WORKDIR $CLOJUPYTER_PATH
RUN git checkout $CLOJUPYTER_VERSION \
	&& make install

WORKDIR $NOTEBOOK_PATH
EXPOSE $PORT
VOLUME $NOTEBOOK_PATH
CMD ["jupyter", "notebook", "--log-level=ERROR"]
