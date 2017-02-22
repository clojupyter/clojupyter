(ns clojupyter.protocol.nrepl-comm)

(defprotocol PNreplComm
  (nrepl-trace [self])
  (nrepl-interrupt [self])
  (nrepl-eval [self states zmq-comm code parent-header session-id signer ident])
  (nrepl-complete [self code])
  )
