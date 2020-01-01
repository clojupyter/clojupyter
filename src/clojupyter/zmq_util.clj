(ns clojupyter.zmq-util
  (:require [clojure.core.async :as async]
            [io.simplect.compose :refer [C]])
  (:import [org.zeromq SocketType ZContext ZFrame ZMQ ZMQ$Error ZMQ$Poller ZMQ$Socket ZMQException]))

(defmacro with-new-context [[binding] & body]
  `(let [ztx# (zcontext), ~binding ztx#]
    (try ~@body
         (finally (.destroy ztx#)))))

(defmacro with-shadow-context [[binding context] & body]
  (if (and (symbol? binding) context)
    `(let [ztx# (ZContext/shadow ~context)
           ~binding ztx#]
       (try ~@body
            (finally (.destroy ztx#))))
    (throw
     (Exception.
      (str "with-shadow-context: `binding` must be a symbol and `context` must be a ZContext: "
           binding ", " context)))))

(defmacro ^{:style/indent :defn} rebind-context-shadowing [[sym] & body]
  (if (symbol? sym)
    `(with-shadow-context [~sym ~sym] ~@body)
    (throw (Exception. (str "rebind-context-shadowing: `sym` must be a symbol: " sym)))))

(defmacro zmq-thread
  [& body]
  `(async/thread
     (try ~@body
          (catch ZMQException e2#
            (let [termcode# (.getCode ZMQ$Error/ETERM)]
              (when-not (= termcode# (.getErrorCode e2#))
                (throw e2#))))
          (catch zmq.ZError$CtxTerminatedException e1#
            nil)
          (catch java.nio.channels.ClosedByInterruptException e3#
            nil))))

(def EMPTY-SEGMENT (byte-array []))

(defn socket-type
  [socket-type-kw]
  (case socket-type-kw
    :dealer	(SocketType/DEALER)
    :pair	(SocketType/PAIR)
    :pub	(SocketType/PUB)
    :pull	(SocketType/PULL)
    :push	(SocketType/PUSH)
    :rep	(SocketType/REP)
    :req	(SocketType/REQ)
    :router	(SocketType/ROUTER)
    :stream	(SocketType/STREAM)
    :sub	(SocketType/SUB)
    :xpub	(SocketType/XPUB)
    :xsub	(SocketType/XSUB)
    (throw (Exception. (str "Unknown socket-type: " socket-type-kw)))))

(def ^Integer socket-type-code (C socket-type #(.type %)))

(defn zcontext ^ZContext
  []
  (ZContext.))

(defn zsocket ^ZMQ$Socket
  [^ZContext ztx socket-type-kw]
  (.createSocket ztx (socket-type-code socket-type-kw)))

(defn receive-frames
  [^ZMQ$Socket zsock]
  (when-let [first-frame (.recv zsock)]
    (loop [frames [first-frame], has-more? (.hasReceiveMore zsock)]
      (if has-more?
        (recur (conj frames (.recv zsock)) (.hasReceiveMore zsock))
        frames))))

(defn send-frames
  [^ZMQ$Socket zsock frames]
  (let [n (-> frames count dec)
        send-it (fn [frame idx]
                  (.send zsock frame (if (= idx n) 0 ZFrame/MORE)))]
    (reduce #(and %1 %2) (map send-it frames (range)))))

(defn- poll-event
  [event-kw]
  (case event-kw
    :pollerr ZMQ$Poller/POLLERR
    :pollin ZMQ$Poller/POLLIN
    :pollout ZMQ$Poller/POLLOUT
    (throw (Exception. (str "Unknown event: " event-kw)))))

(defn poll-events ^Integer
  [& event-kws]
  (reduce (fn [Σ kw] (bit-or Σ (poll-event kw))) 0 event-kws))
