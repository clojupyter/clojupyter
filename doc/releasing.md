# Releasing/Deploying to clojars

Unfortunately I can no longer support installation via conda. Instead we use https://github.com/slipset/deps-deploy to release to https://clojars.org/

## Release commands
TODO: convert to GH Actions (https://github.com/clojupyter/clojupyter/issues/179).

### Build

```bash
clj -T:build jar
```

### Unit test

```
clj -X:test
Running tests in namespaces
[:all]
nil
All checks (117) succeeded.

```

### Manual integration testing
(Requires Jupyterlab installed locally)
This can likely only be done easily by @krukow

- Clone https://github.com/krukow/edmondson
- Update edmondson repo deps.edn `:jupyter` alias should use {:local/root "PATH-TO:clojupyter/target/clojupyter-[x.y.z].jar"}
- Run `./script/go.sh` to build uberjar kernel of Edmondson+Clojupyter and install it as a Jupyter lab kernel
- Ensure you have a valid token (Requires Google credentials
- Test by Running https://github.com/krukow/edmondson/blob/main/examples/google_sheets/psych_safety_generative_culture.ipynb on Edmondson kernel - validate results look right and no errors/exceptions in log

### Prepare deploy to clojars

From clojupyter repo directory - copy pom.xml for release (unfortunately it doesn't look like we can specify this on the command line so we copy to the expected location for deps-deploy).

```
cp target/classes/META-INF/maven/clojupyter/clojupyter/pom.xml ./
```

Get the expected tag: `cat pom.xml | grep tag`, e.g. `v0.4.334` or run `clj -M -m clojupyter.cmdline version`

Use git to commit and push the changes in the version (`resources/clojupyter/assets/version.edn`)

Use git to tag and push the release tag: (e.g. `git tag v0.4.329`, `git push origin tag v0.4.329`)

### Sign and deploy to clojars

This can only be done by @krukow at the moment (due to signing).

Replace username, password, and GPG passphrase; replace :artifact.

env CLOJARS_USERNAME=... CLOJARS_PASSWORD=... clj -X:deploy  :artifact "target/clojupyter-X.Y.Z.jar"

Enter the gpg passphrase: ...
