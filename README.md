# Snowball

View the sizes of your dependencies.

## Usage

Create an alias for snowball

```clojure

{
 :aliases {
           :snowball {:exec-fn com.phronemophobic.snowball/-main}
 }
}
```

## Examples


Show the treemap for the local deps file "deps.edn"

        clojure -X:snowball :deps deps.edn

Print the sizes for the local deps file "deps.edn"

        clojure -X:snowball :deps deps.edn :view print

Save a treemap image to "sizes.png" for the local deps file "deps.edn"

        clojure -X:snowball :deps deps.edn :view treemap-image :path sizes.png

Use a specific Maven version

        clojure -X:snowball :lib cnuernber/dtype-next :mvn/version  '"9.011"'

Use a specific git version

        clojure -X:snowball :lib thheller/shadow-cljs :git/url '"https://github.com/thheller/shadow-cljs"' :git/sha '"46b73e161732d3a38a0c797119260775b78c8e93"'

Use a local root in the current directory "."

        clojure -X:snowball :lib my-local/lib :local/root .


## Options

### Required

Either `:deps` or `:lib` must be specified.

`:lib`: The lib coordinate name. Example: `org.clojure/clojure`.  
`:deps`: The path to a `deps.edn` path.  

### Optional

`:view`: One of `treemap`, `treemap-image`, or `print`.  
    `treemap` (default): This will open a Swing window that shows a treemap of the dependencies. Area corresponds to size.  
    `treemap-image`: Save a treemap as an image to :path. Default :path is "snowball.png".  
    `print`: Prints a table of dependencies and sizes sorted by size.  


`:path`: Specify a path when used with :view treemap-image. Ignored otherwise.

#### Specifying a version

`:mvn/version`: mvn-version
	
`:git/sha`: sha  
`:git/url`: url
	
`:local/root`: root
	
`:deps`: path-to-deps.edn


## License

Copyright © 2021 Adrian

Distributed under the Eclipse Public License version 1.0.
