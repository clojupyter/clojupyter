# ipython-clojure
A IPython kernel written in Clojure, this will let you run Clojure code from the IPython console and notebook

![ipython-clojure](https://raw.github.com/roryk/ipython-clojure/master/images/demo.gif)

## using it
1. run make
2. make a new profile with `ipython profile create clojure`
3. add these lines to your ipython_config.py located in .ipython/profile_clojure/ipython_config.py

```
# Set the kernel command.
c = get_config()
c.KernelManager.kernel_cmd = ["/Users/rory/cache/ipython-clojure/bin/ipython-clojure",
                              "{connection_file}"]

# Disable authentication.
c.Session.key = b''
c.Session.keyfile = b''
```

4. run the repl with `ipython console --profile clojure`

## status
Should work for simple stuff in the REPL. Doesn't handle errors or any type of complex data from Clojure.
