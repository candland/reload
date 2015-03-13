# Reload

_ALPHA_

Used to load a new JAR into memory before killing the currently running JAR. 


## Releases and Dependency Information

* Releases are published to [Clojars]

* Latest stable release is 0.0.1-SNAPSHOT

* [All released versions](https://clojars.org/net.candland/reload)

[Leiningen] dependency information:

    [net.candland/reload "0.0.1-SNAPSHOT"]

[Maven] dependency information:

    <dependency>
      <groupId>net.candland</groupId>
      <artifactId>reload</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </dependency>

[Clojars]: https://clojars.org/
[Leiningen]: http://leiningen.org/
[Maven]: http://maven.apache.org/


## Usage

Starting the application. Below is an example of starting a jar and setting
the command line options needed for it to start new processes.

```bash
java -jar target/my-uberjar-standalone.jar \
  -d \
  -ePORT:5000 \
  -eENV:production \
  -l/apps/my/shared/log/production.log \
  -p/apps/my/shared/pids/my.pid \
  -c/apps/my/current/
```

There are two integration points, `-main` and the point where the application
is started and ready to start handling load.

In the main function, we initialize reload with the command to reload upon
signal. This should be the same command that is used to start the program 
initially with out the command line arguments. The args will be passed to 
the new process.


```clojure
(defn -main [& args]
  (let [command ["java" "-jar" "my-uberjar-standalone.jar"]]
    (reload/initialize command)
    (reload/run main-app args)
    )
  )
```


Restart Point, this is where we kill the other app and start doing work. I'm
using the (Component pattern from Stuart Sierra)
[https://github.com/stuartsierra/component] and do something like this:


```clojure
;; WebServer
(defrecord WebServer [handler-fn cache]

  component/Lifecycle

  (start [component]
    (let [
      handler (handler-fn cache)

      server (reload/clean-and-wait-for-port! 
                (:port config) 
                (run-jetty-fn config handler))]

      (assoc component :server server, :handler handler)
      )
    ) 

  (stop [component]
    (when-not (nil? (:server component))
      (.stop (:server component))
      )
    (dissoc component :server)
    )
  )
```

The `clean-and-wait-for-port!` will kill the currently running process and 
wait for the port to become available. This takes a few milliseconds. 


## Change Log

* Version 0.0.1-SNAPSHOT



## Copyright and License

The MIT License (MIT)

Copyright © 2015 Dusty Candland

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

