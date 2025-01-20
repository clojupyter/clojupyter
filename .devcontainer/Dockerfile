ARG BASE_IMAGE=temurin-21-tools-deps-jammy
FROM clojure:${BASE_IMAGE}

ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID
    
# Create the user
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME \
    #
    # [Optional] Add sudo support. Omit if you don't need to install software after connecting.
    && apt-get update \
    && apt-get install -y sudo \
    && echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME
    
    
# [Optional] Set the default user. Omit if you want to keep the default as root.
USER $USERNAME
SHELL ["/bin/bash", "-ec"]
ENTRYPOINT ["bash"]


# Prepare clojure tools
RUN clojure -Ttools list && \
clojure -Ttools install io.github.seancorfield/clj-new '{:git/tag "v1.2.404" :git/sha "d4a6508"}' :as clj-new && \
clojure -Ttools install-latest :lib io.github.seancorfield/deps-new :as new && \
clojure -Ttools list

RUN sudo apt-get update && \
    sudo apt-get install -y lsb-release leiningen

