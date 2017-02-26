(ns clojupyter.protocol.zmq-comm)

(defprotocol PZmqComm
  (zmq-send [self socket message zmq-flag] [self socket message])
  (zmq-read-raw-message [self socket])
  (zmq-recv [self socket])
  (zmq-recv-all [self socket]))
