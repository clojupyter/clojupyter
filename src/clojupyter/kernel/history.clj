(ns clojupyter.kernel.history
  (:require [clojupyter.kernel.config :as cfg]
            [clojupyter.state :as state]
            [clojupyter.util-actions :as u!]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as sql]))

(def ^:private JUPYTER-HISTORY-FILE "jupyter-history")

(defn- history-file
  []
  (io/file
   (str (cfg/clojupyter-datahome) "/" JUPYTER-HISTORY-FILE)))

(defn init-history
  ([] (init-history (history-file)))
  ([db-file]
   (let [has-db-file (.exists (io/file db-file))
         db {:classname   "org.sqlite.JDBC",
             :subprotocol "sqlite",
             :subname      db-file}]
     (if (not has-db-file)
       (let [history-table (sql/create-table-ddl :history
                                                 [[:session    :integer]
                                                  [:line       :integer]
                                                  [:source     :text]
                                                  [:primary :key "(session, line)"]])
             session-table (sql/create-table-ddl :sessions
                                                 [[:session  :integer
                                                   :primary :key
                                                   :autoincrement]
                                                  [:start    :timestamp]
                                                  [:end      :timestamp]
                                                  [:num_cmds :integer
                                                   :default "0"]
                                                  [:remark   :text]])]
         (sql/execute! db [history-table])
         (sql/execute! db [session-table])))
     db)))

(defn start-history-session
  [db]
  (sql/insert! db :sessions {:start (u!/java-util-data-now)})
  {:db         db
   :session-id ((keyword "max(session)")
                (first (sql/query db
                                  "select max(session) from sessions")))})
(defn end-history-session!
  ([] (end-history-session! (:history-session @state/STATE)))
  ([session] (end-history-session! session 5000))
  ([session max-history]
   (let [db (:db session)]
     (sql/execute! db ["delete from history
                          where rowid not in (select rowid from history
                          order by session desc, line desc
                          limit ?)", max-history])
     (sql/update! db :sessions {:end (u!/java-util-data-now)}  ["session = ?" (:session-id session)]))))

(defn add-history!
  ([source] (add-history! (:history-session @state/STATE) (:execute-count @state/STATE) source))
  ([session line source]
   (sql/execute! (:db session)
                 ["update sessions set num_cmds = num_cmds + 1
                   where session = ?", (:session-id session)])
   (sql/insert! (:db session) :history
                {:session (:session-id session)
                 :line line
                 :source source})
   session))

(defn get-history
  ([] (get-history (:history-session @state/STATE)))
  ([session]
   (sql/query (:db session)
              ["select * from history"])))
