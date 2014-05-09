(ns ipython-clojure.core-test
  (:use [midje.sweet])
  (:require [clojure.test :refer :all]
            [ipython-clojure.core :refer :all]))

(fact "setting up a zeromq socket works"
      (let [socket (setup-publisher 127.0.0.1 4891)]
        (write-message socket "ping") => "ok")

;; this is what we get called with originally

(def test-config {"stdin_port" 48691
                  "ip" 127.0.0.1
                  "control_port" 44808
                  "hb_port" 49691
                  "signature_scheme" "hmac-sha256"
                  "key" ""
                  "shell_port" 49332
                  "transport" "tcp"
                  "iopub_port" 49331})

;; we need to create sockets for each port and bind them
;; look up how to bind sockets in java
;; those sockets will get messages that we have to respond to
;; all sockets except the heartbeat get messages like this

(def example-message ["u-u-i-d", "<IDS|MSG>", "baddad42","{header}",
                      "{parent_header}", "{metadata}", "{content}","blob"])

;; dictionaries are serialized json

;; two messages to respond to
;; "kernel_info_request" a "kernel_info_reply" should be sent back
;; has information about these
;; http://ipython.org/ipython-doc/dev/development/messaging.html#kernel-info

;; "execute_request" must eventually send an "execute_reply" request
;; as "ok", "error" or "abort"

;; on the hb_port we just need to echo anything that comes to it
;; set that up first

;; find a clojure zeromq library and use that to bind to the ports
;; at least get something functional that responds
;; set up with channels, something like that for clojure, core.async
;; might be the solution for what we want to do

(require '[com.keminglabs.zmq-async.core :refer [register-socket!]]
         '[clojure.core.async :refer [>! <! go chan sliding-buffer close!]])

(let [n 3, addr "inproc://ping-pong"
      [s-in s-out c-in c-out] (repeatedly 4 #(chan (sliding-buffer 64)))]

  (register-socket! {:in s-in :out s-out :socket-type :rep
                     :configurator (fn [socket] (.bind socket addr))})
  (register-socket! {:in c-in :out c-out :socket-type :req
                     :configurator (fn [socket] (.connect socket addr))})

  (go (dotimes [_ n]
        (println (String. (<! s-out)))
        (>! s-in "pong"))
    (close! s-in))

  (go (dotimes [_ n]
        (>! c-in "ping")
        (println (String. (<! c-out))))
    (close! c-in)))
