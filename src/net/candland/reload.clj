(ns net.candland.reload
  (:require [beckon :as b]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class)
  )

(import java.io.File)

(def ^:private _logfile (atom nil))
(def ^:private _pidfile (atom nil))
(def ^:private _options (atom nil))
(def ^:private _command (atom nil))


(defn current-pid []
  "Get current process id." 
  (-> (java.lang.management.ManagementFactory/getRuntimeMXBean)
    (.getName)
    (clojure.string/split #"@")
    (first)))

(defn current-working-dir []
  (System/getProperty "user.dir"))

(defn expand-path [path]
  (clojure.string/replace path "~" (System/getProperty "user.home")))


(defn initialize [command]
  "Expects a Seq of strings for the command to restart."
  (reset! _command command)
  )


(defn fork-app []
  "Starts a new application using the initialized command."
  (prn "OPTIONS" @_options)
  (let [working-dir (or (:cwd @_options) (current-working-dir))
        environment (or (:env @_options) {})
        log-arg     (when @_logfile (str "-l" @_logfile)) 
        pid-arg     (when @_pidfile (str "-p" @_pidfile)) 
        cwd-arg     (when (:cwd @_options) (str "-c" (:cwd @_options))) 
        env-arg     (when (:env @_options) 
                      (map (fn [[k,v]] (str "-e" k ":" v)) (:env @_options)))
        command     (flatten (filter identity 
                      (conj @_command log-arg, pid-arg, cwd-arg, env-arg, 
                                    :dir working-dir, :env environment)))
        _           (prn "CMD" command)
        result      (apply sh/sh command)]
    (prn (:out result))
    (prn (:err result))
    (System/exit (:exit result))
    )
  )


(defn clean-old-app! []
  "Looks for <pidfile>.old, and if found kills that application. Should be
   called at the last monment."
  (let [old-pidfile (str @_pidfile ".old")]
    (when (.exists (File. old-pidfile))
      (let [pid (slurp old-pidfile)]
        (prn (str "Killing " pid))
        (let [kill-resp (sh/sh "kill" pid)]
          (prn "KILL" kill-resp)
          (when-not (= (:exit kill-resp) 0)
            (throw (Exception. (str "Failed to kill process " pid ": "
                                    (:err kill-resp))))))
        (.delete (File. old-pidfile))
        )
      )
    )
  )

(defn with-free-port!
  "Repeatedly executes nullary `bind-port!-fn` (which must return logical true on successful
  binding). Returns the function's result when successful, else throws an
  exception. *nix only.
 
  This idea courtesy of Feng Shen, Ref. http://goo.gl/kEolu."
  [port bind-port!-fn & {:keys [max-attempts sleep-ms]
                         :or   {max-attempts 50
                                sleep-ms     150}}]
      
  
  (prn (str "Freeing port " port))
  (loop [attempt 1]
    (prn (str "Attempting to bind to " port))
    (when (> attempt max-attempts)
      (throw (Exception. (str "Failed to bind to port " port " within "
                              max-attempts " attempts ("
                              (* max-attempts sleep-ms) "ms)"))))
    (if-let [result (try (bind-port!-fn) (catch java.net.BindException _))]
      (do (prn (str "Bound to port " port " after "
                            attempt " attempt(s)"))
          result)
      (do (Thread/sleep sleep-ms)
          (recur (inc attempt)))
      )
    )
  )

(defn clean-and-wait-for-port!
  "Kill existing process and wait for port to free up."
  [port bind-port!-fn & options]
  (prn "Calling clean-old-app")
  (clean-old-app!)
  (prn "Calling with-free-port")
  (with-free-port! port bind-port!-fn)
  )


(defn usr2-fn []
  "Handles the USR2 function by looking for an old file and 'forking' this app"
  (fn [] 
    (prn "Reloading" (current-pid))
 
    (let [old-pid-file (str @_pidfile ".old")]
      (when (.exists (File. old-pid-file))
        (prn "FYI; Failed to kill previous process." (slurp old-pid-file))
        (.delete (File. old-pid-file))
        )

      (spit old-pid-file (current-pid))
      (fork-app)
      )
    )
  )


(defn run-app [app-fn]
  "Runs the app-fn supplied. Writes to pidfile, if set."
  (reset! (b/signal-atom "USR2") #{(usr2-fn)})
  (app-fn)
  )


(def ^:private cli-options
  [["-h" "--help" "Help"]
   ["-d" "--daemonize" "daemonize" :default false]
   ["-l" "--logfile PATH" "Logfile" :parse-fn #(str %), :default nil]
   ["-p" "--pidfile PATH" "PID File" :parse-fn #(str %), :default "run.pid"]
   ["-c" "--cwd PATH" "Change working directory" :parse-fn expand-path]
   ["-e" "--env NAME:VALUE" "environment values" :default {}, 
      :assoc-fn (fn [m k v] 
        (let [[env-key env-val] (clojure.string/split v #":")]
          (update-in m [k] assoc env-key env-val)))]
   ])


(defn run [run-fn args]
  "run-fn, the function to run if not daemonized.
    -h, --help                     Help
    -d, --daemonize                daemonize
    -l, --logfile PATH             Logfile
    -p, --pidfile PATH    run.pid  PID File
    -c, --cwd PATH                 Change working directory
    -e, --env NAME:VALUE  {}       environment values"
  (let [opts (parse-opts args cli-options)]
    
    (when (or (:error opts) (get-in opts [:options :help]))
      (println (:summary opts))
      (System/exit 1))

    (let [options (:options opts)
          daemonize (:daemonize options)]

      (reset! _logfile (:logfile options))
      (reset! _pidfile (:pidfile options))
      (reset! _options options)
      (spit @_pidfile (current-pid))

      (let [logwriter (if-not (nil? @_logfile) (io/writer @_logfile :append true) *out*)
            errwriter (if-not (nil? @_logfile) logwriter *err*)]
        (with-redefs [*out* logwriter, *err* errwriter]
          (if daemonize 
            (do (prn "Starting...") 
                ((usr2-fn)))
            (do (prn "Running...") 
                (run-app run-fn)))
          )
        )
      )
    )
  )

