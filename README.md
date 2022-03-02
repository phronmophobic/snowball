# Snowball

View the sizes of your dependencies.


### Treemap

```sh
clojure -X:snowball :lib thheller/shadow-cljs :view treemap-image
```
![shadow-cljs](/snowball.png?raw=true)

### Table in terminal

```sh
clojure -X:snowball :lib thheller/shadow-cljs :view print | head -12
```
```sh
                 namespace |                               name | transitive-size |     self-size
-------------------------------------------------------------------------------------------------
            org.graalvm.js |                                 js |      43,361,897 |    18,305,220
               com.ibm.icu |                              icu4j |      13,298,680 |    13,298,680
     com.google.javascript |          closure-compiler-unshaded |      13,008,835 |    13,008,835
       org.graalvm.truffle |                        truffle-api |       8,362,026 |     8,362,026
               org.clojure |             google-closure-library |       5,970,746 |     5,970,746
               org.clojure |                      clojurescript |       4,892,746 |     4,892,746
               org.clojure |                            clojure |       4,539,616 |     3,914,649
                  thheller |                        shadow-cljs |      78,811,694 |     2,988,201
         org.graalvm.regex |                              regex |       2,816,757 |     2,816,757
               io.undertow |                      undertow-core |       3,530,146 |     2,343,146
```

## Usage

Create an alias for snowball

```clojure

{
 :aliases {
```
```clojure
           :snowball
           {:exec-fn com.phronemophobic.snowball/-main
            :replace-deps {com.phronemophobic/snowball {:mvn/version "1.2"}}}
```
```clojure
           }
}
```

## Examples


Show the interactive treemap for the local deps file "deps.edn"

```sh
clojure -X:snowball :deps deps.edn
```

Print the sizes for the local deps file "deps.edn"

```sh
clojure -X:snowball :deps deps.edn :view print
```

Save a treemap image to "sizes.png" for the local deps file "deps.edn"

```sh
clojure -X:snowball :deps deps.edn :view treemap-image :path sizes.png
```

Use a specific Maven version

```sh
clojure -X:snowball :lib cnuernber/dtype-next :mvn/version  '"9.011"'
```

Use a specific git version

```sh
clojure -X:snowball :lib thheller/shadow-cljs :git/url '"https://github.com/thheller/shadow-cljs"' :git/sha '"46b73e161732d3a38a0c797119260775b78c8e93"'
```

Use a local root in the current directory "."

```sh
clojure -X:snowball :lib my-local/lib :local/root .
```


## Options

### Required

Either `:deps` or `:lib` must be specified.

`:lib`: The lib coordinate name. Example: `org.clojure/clojure`.  
`:deps`: The path to a `deps.edn` path.  

### Optional

`:view`: One of the following:  
* `treemap` (default): This will open a Swing window that shows a treemap of the dependencies. Area corresponds to size.  
* `treemap-image`: Save a treemap as an image to :path. Default :path is "snowball.png".  
* `print`: Prints a table of dependencies and sizes sorted by size.  
* `csv`: Prints a csv list of dependencies and sizes.  
* `edn`: Prints an edn map of dependencies and sizes.  
* `json`: Prints a json map of dependencies and sizes.  
* `treemap-json`: Prints a the treemap layout as json. See [description](https://github.com/phronmophobic/treemap-clj#rendering-your-own-treemap-layers).  
* `treemap-edn`: Prints a the treemap layout as edn. See [description](https://github.com/phronmophobic/treemap-clj#rendering-your-own-treemap-layers).  

`:path`: Specify a path when used with :view treemap-image. Ignored otherwise.

#### Specifying a version

You can use a specific version by providing one of the following. If no version is provided, then `{:mvn/version "RELEASE"}` is used.

`:mvn/version`: mvn-version

`:git/sha`: sha  
`:git/url`: url
	
`:local/root`: root
	
`:deps`: path-to-deps.edn

## Usage with lein projects

Snowball doesn't work directly with lein projects, but it can be run against any mvn library so the workaround is something like:

```sh
$ lein install
Created /Users/adrian/workspace/membrane-re-frame-example/target/membrane-re-frame-example-0.1.0-SNAPSHOT.jar
Wrote /Users/adrian/workspace/membrane-re-frame-example/pom.xml
Installed jar and pom into local repo.
# Note the project name and version
$ clojure -X:snowball :lib membrane-re-frame-example :mvn/version '"0.1.0-SNAPSHOT"'
```


## Related

[tools.deps.graph](https://github.com/clojure/tools.deps.graph): A tool for making deps.edn dependency graphs.

## License

Copyright © 2021 Adrian

Distributed under the Eclipse Public License version 1.0.
