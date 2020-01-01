(ns clojupyter.kernel.dispatch-test
  (:require
   [clojure.spec.alpha			:as s]
   [midje.sweet				:as midje	:refer [fact throws =>]]
   ,,
   [clojupyter.kernel.handle-event	:as he]
   [clojupyter.messages			:as msgs]
   [clojupyter.test-shared		:as ts]
   ))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; GENERATE ERRORS FOR MESSAGES WE EXPECT TO NEVER RECEIVE
;;; ------------------------------------------------------------------------------------------------------------------------

(fact
 "Receipt of unknown Jupyter messages throws an error."
 (he/handle-event! (str (gensym)))
 => (throws Exception))

(fact
 "Receipt of 'impossible' messages (cf. dispatch.clj) throw errors."
 (he/handle-event! msgs/CLEAR-OUTPUT)		=> (throws Exception)
 (he/handle-event! msgs/COMM-INFO-REPLY)	=> (throws Exception)
 (he/handle-event! msgs/COMPLETE-REPLY)		=> (throws Exception)
 (he/handle-event! msgs/ERROR)			=> (throws Exception)
 (he/handle-event! msgs/EXECUTE-INPUT)		=> (throws Exception)
 (he/handle-event! msgs/EXECUTE-REPLY)		=> (throws Exception)
 (he/handle-event! msgs/EXECUTE-RESULT)		=> (throws Exception)
 (he/handle-event! msgs/HISTORY-REPLY)		=> (throws Exception)
 (he/handle-event! msgs/INPUT-REQUEST)		=> (throws Exception)
 (he/handle-event! msgs/INSPECT-REPLY)		=> (throws Exception)
 (he/handle-event! msgs/INTERRUPT-REPLY)	=> (throws Exception)
 (he/handle-event! msgs/INTERRUPT-REQUEST)	=> (throws Exception)
 (he/handle-event! msgs/IS-COMPLETE-REPLY)	=> (throws Exception)
 (he/handle-event! msgs/KERNEL-INFO-REPLY)	=> (throws Exception)
 (he/handle-event! msgs/SHUTDOWN-REPLY)		=> (throws Exception)
 (he/handle-event! msgs/STATUS)			=> (throws Exception)
 (he/handle-event! msgs/STREAM)			=> (throws Exception)
 (he/handle-event! msgs/UPDATE-DISPLAY-DATA)	=> (throws Exception))

(fact
 "Receipt of 'unsupported' messages (cf. dispatch.clj) throw errors."
 (he/handle-event! msgs/PROTOCOL-VERSION)	=> (throws Exception))

