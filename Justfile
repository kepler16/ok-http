test:
    clojure -X:test

clean:
    clojure -T:build clean 

build: clean
    clojure -T:build jar

release:
    clojure -T:build release
