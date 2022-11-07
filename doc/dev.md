# Development

This project has historically used `lein` () but is slowly migrating towards
tools.deps/cli ().

Most standard clojure development environments should work, but `@krukow` is
using Clojure tools.cli, and Emacs with cider.

## Testing

The project uses Midje for most tests. You can run the tests interactively in
your repl (), or from the CLI.


Examples:


Just tests in `clojupyter.messages-tests`:

```
clj -X:test :ns '[clojupyter.messages-tests]'
```


Just tests in `clojupyter.messages-tests` with `:print-facts` option.


```
clj -X:test :ns '[clojupyter.messages-tests]' :opts '[:print-facts]'
```

Run everything (all Midje tests)

```
clj -X:test
```
