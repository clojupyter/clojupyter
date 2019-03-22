(ns clojupyter.misc.spec
  (:require
   [clojure.spec.alpha			:as s]))

(s/def ::byte-array		#(= (type %) (Class/forName "[B")))

(s/def ::jupyter-config		(s/keys :req-un [::control_port ::shell_port ::stdin_port ::iopub_port ::hb_port
                                                 ::ip ::transport ::key]))

(s/def ::ctx			(s/keys :req-un [::nrepl-comm ::transport ::checker ::signer
                                                 ::parent-message]
                                        :opt-un [ ::msgtype]))

(s/def ::msg_id			string?)
(s/def ::msg_type		string?)
(s/def ::session		string?)
(s/def ::date			string?)
(s/def ::version		string?)
(s/def ::header			(s/keys :req-in [::msg_id ::msg_type ::username ::session ::date ::version]))

(s/def ::envelope		(s/coll-of ::byte-array))
(s/def ::delimiter		(s/and string? (partial = "<IDS|MSG>")))
(s/def ::signature		(s/and string? (s/or :full (partial re-find #"^[0-9a-f]{64}$")
                                                     :empty (partial = ""))))
(s/def ::parent-header		(s/or :empty (partial = {}), :header ::header))
(s/def ::metadata		map?)
(s/def ::content		map?)

(s/def ::jupyter-message	(s/keys :req-un [::envelope ::delimiter ::signature
                                                 ::header ::parent-header ::content]
                                        :req-opt [::metadata]))
