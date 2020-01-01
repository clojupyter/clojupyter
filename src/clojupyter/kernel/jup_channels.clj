(ns clojupyter.kernel.jup-channels
  "Defines the interface for communicating with the Clojupyter kernel which, to ensure close alignment
  with Jupyter's architecture, is based on `core.async` channels (mimicking the ZeroMQ channels used
  for communication between the Jupyter client (Notebook, Lab, others) and backend (kernels such as
  Clojupyter)."
  (:require [clojure.core.async :as async]
            [clojure.pprint :as pp]))

(defprotocol channels-proto
  (jup-channel [jup socket-kw inbound-or-outbound]
    "Returns the channel associated with `socket-kw`, `inbound-or-outbound` must be either
    `:inbound` or `:outbound`.")
  (jup-channels [jup socket-kw]
    "Returns a 2-tuple comprising the inbound and outbound channels for the Jupyter socket
    `socket-kw`.")
  (receive!! [jup socket-kw]
    "Receive from channel associated with `socket-kw`.")
  (send!!
    [jup socket-kw req-message msgtype content]
    [jup socket-kw req-message msgtype metadata content]
    "Send to channel associated with `socket-kw`."))

(defn- fmt [_] "#Jup")

(defrecord Jup [ctrl-in ctrl-out
                shell-in shell-out
                io-in io-out
                st-in st-out]
  channels-proto
  (jup-channel [jc socket-kw inbound-or-outbound]
    (let [select (case inbound-or-outbound
                   :inbound first
                   :outbound second
                   (throw "channel: `inbound-or-outbound` must be either `:inbound` or `:outbound`."))]
      (select (jup-channels jc socket-kw))))
  (jup-channels [jc socket-kw]
    (case socket-kw
      :control_port [ctrl-in ctrl-out]
      :shell_port   [shell-in shell-out]
      :iopub_port   [io-in io-out]
      :stdin_port   [st-in st-out]
      (throw (Exception. (str "Unknown socket: " socket-kw)))))
  (receive!! [jup rcv-socket-kw]
    (async/<!! (jup-channel jup rcv-socket-kw :inbound)))
  (send!! [jup rsp-socket-kw req-message rsp-msgtype rsp-content]
    (send!! jup rsp-socket-kw req-message rsp-msgtype {} rsp-content))
  (send!! [jup rsp-socket-kw req-message rsp-msgtype rsp-metadata rsp-content]
    (let [ch (jup-channel jup rsp-socket-kw :outbound)]
      (async/>!! ch {:rsp-content rsp-content
                     :rsp-msgtype rsp-msgtype
                     :rsp-socket rsp-socket-kw
                     :rsp-metadata rsp-metadata
                     :req-message req-message})))
  Object
  (toString [jc] (fmt jc)))

(alter-meta! #'->Jup assoc :private true)

(defmethod print-method Jup
  [^Jup t w]
  (.write w "#Jup"))

(defmethod pp/simple-dispatch Jup
  [^Jup jc]
  (print (fmt jc)))

(defn make-jup
  [ctrl-in  ctrl-out
   shell-in shell-out
   iopub-in iopub-out
   stdin-in stdin-out]
  (->Jup ctrl-in    ctrl-out
         shell-in   shell-out
         iopub-in   iopub-out
         stdin-in   stdin-out))

(defn jup?
  [v]
  (instance? Jup v))
