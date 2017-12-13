(ns clojupyter.misc.zmq-comm
  (:require [clojure.pprint :as pp]
            [clojupyter.protocol.zmq-comm :as pzmq]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]))

(defn- string-to-bytes [s]
  (. (. (java.nio.charset.Charset/forName "UTF-8") encode s) array))

(defn parts-to-message [parts]
  (let [delim "<IDS|MSG>"
        delim-byte (string-to-bytes delim)
        delim-idx (first
                   (map first (filter #(apply = (map seq [(second %) delim-byte]))
                                      (map-indexed vector parts))))
        idents (take delim-idx parts)
        blobs (map #(new String % "UTF-8")
                   (drop (inc delim-idx) parts))
        blob-names [:signature :header :parent-header :metadata :content]
        n-blobs (count blob-names)
        message (merge
                 {:idents idents :delimiter delim}
                 (zipmap blob-names (take n-blobs blobs))
                 {:buffers (drop n-blobs blobs)})]
    message))

(defrecord ZmqComm [shell-socket iopub-socket control-socket hb-socket]
  pzmq/PZmqComm
  (zmq-send [self socket message]
    (apply zmq/send
           [@(get self socket) message]))
  (zmq-send [self socket message zmq-flag]
    (apply zmq/send
           [@(get self socket) message zmq-flag]))
  (zmq-read-raw-message [self socket]
    (let [parts (pzmq/zmq-recv-all self socket)
          message (parts-to-message parts)]
      (log/info "Receive message\n"
                (with-out-str (pp/pprint message)))
      message))
  (zmq-recv [self socket]
    (apply zmq/receive
           [@(get self socket)]))
  (zmq-recv-all [self socket]
    (apply zmq/receive-all
           [@(get self socket)])))

(defn make-zmq-comm [shell-socket iopub-socket
                     control-socket hb-socket]
  (ZmqComm. shell-socket iopub-socket
            control-socket hb-socket))
