(require '[cljs.repl :as repl])
(require '[cljs.repl.node :as node])
(def env (node/repl-env))
(repl/repl env)

(js/console.log "Hello CLJS!")