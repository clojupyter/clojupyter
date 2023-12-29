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


## Build


Building a jar:

```
clj -T:build jar
Checking out: https://github.com/grammarly/omniconf.git at d71662749c6b2e5baf80e9e059351a343674330e
Skipping coordinate: {:git/sha d71662749c6b2e5baf80e9e059351a343674330e, :git/url https://github.com/grammarly/omniconf.git, :deps/manifest :deps, :deps/root /Users/krukow/.gitlibs/libs/io.github.grammarly/omniconf/d71662749c6b2e5baf80e9e059351a343674330e, :parents #{[]}, :paths [/Users/krukow/.gitlibs/libs/io.github.grammarly/omniconf/d71662749c6b2e5baf80e9e059351a343674330e/src]}
target/clojupyter-0.4.313.jar
```

Building an uberjar:

```
clj -T:build uber
```

## Release



``` bash
$ cat ~/.m2/settings.xml
<settings>
    <servers>
        <server>
            <id>${repo.id}</id>
            <username>${repo.login}</username>
            <password>${repo.pwd}</password>
        </server>
    </servers>
</settings>

$ clj -T:build pom

$ mvn deploy -DaltDeploymentRepository=clojars::https://repo.clojars.org/ -Drepo.id=clojars -Drepo.login=krukow -Drepo.pwd="..."
```
