(ns clojupyter.protocol.nrepl-comm)

(defprotocol PNreplComm
  (nrepl-trace [self])
  (nrepl-interrupt [self])
  (nrepl-eval [self S code parent-message])
  (nrepl-complete [self code])
  (nrepl-doc [self sym]))
