---
layout: docs
toc_group: js
link_title: ScriptEngine Implementation
permalink: /reference-manual/js/ScriptEngine/
---
# ScriptEngine Implementation

GraalVM provides a JSR-223 compliant `javax.script.ScriptEngine` implementation for running JavaScript.
Note that this feature is provided for legacy reasons in order to allow easier migration for implementations currently based on a `ScriptEngine`.
We strongly encourage users to use the `org.graalvm.polyglot.Context` interface in order to control many of the settings directly and benefit from finer-grained security settings in GraalVM.

## Prerequisite

NOTE: Beginning with version 23.1.0, GraalVM no longer includes a JS ScriptEngine by default.
If you relied on that, you will have to migrate your setup to explicitly depend on the script engine module and add it to the _module path_.

To get the `js-scriptengine` module, we recommend to use a Maven dependency, like follows:
```xml
<dependency>
    <groupId>org.graalvm.js</groupId>
    <artifactId>js-scriptengine</artifactId>
    <version>${graalvm.version}</version>
</dependency>
<dependency>
    <groupId>org.graalvm.js</groupId>
    <artifactId>js</artifactId>
    <version>${graalvm.version}</version>
    <scope>runtime</scope>
</dependency>
```

If you're not using `mvn`, you will need to add the `js-scriptengine.jar` file to the module path manually, e.g.: `--module-path=languages/js/graaljs-scriptengine.jar`.
In some case, you may also need to add `--add-modules org.graalvm.js.scriptengine` to the command line, to ensure that the `ScriptEngine` will be found.
An explicit dependency on the `org.graalvm.js.scriptengine` module is only required if you want to use `GraalJSScriptEngine` directly (see below).
Finally, it's also possible to use `jlink` to generate a custom Java runtime image that contains the JS ScriptEngine.

An example `pom.xml` can be found [in the graaljs repository on GitHub](https://github.com/oracle/graaljs/blob/master/graal-js/test/maven-demo/pom.xml).

## Recommendation: Use `CompiledScript` API

To avoid unnecessary re-compilation of JS sources, it is recommended to use `CompiledScript.eval` instead of `ScriptEngine.eval`. This prevents
JIT-compiled code from being garbage-collected as long as the corresponding `CompiledScript` object is alive.

Single-threaded example:
```java
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("js");
CompiledScript script = ((Compilable) engine).compile("console.log('hello world');");
script.eval();
```

Multi-threaded example:
```java
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("js");
CompiledScript script = ((Compilable) engine).compile("console.log('start');var start = Date.now(); while (Date.now()-start < 2000);console.log('end');");
new Thread(new Runnable() {
    @Override
    public void run() {
        try {
            // Create ScriptEngine for this thread (with a shared polyglot Engine)
            ScriptEngine engine = manager.getEngineByName("js");
            script.eval(engine.getContext());
        } catch (ScriptException scriptException) {
            scriptException.printStackTrace();
        }
    }
}).start();
script.eval();
```

## Setting Options via `Bindings`
The  `ScriptEngine` interface does not provide a default way to set options.
As a workaround, `GraalJSScriptEngine` supports setting some `Context` options
through `Bindings`.
These options are:
* `polyglot.js.allowHostAccess <boolean>`
* `polyglot.js.allowNativeAccess <boolean>`
* `polyglot.js.allowCreateThread <boolean>`
* `polyglot.js.allowIO <boolean>`
* `polyglot.js.allowHostClassLookup <boolean or Predicate<String>>`
* `polyglot.js.allowHostClassLoading <boolean>`
* `polyglot.js.allowAllAccess <boolean>`
* `polyglot.js.nashorn-compat <boolean>`
* `polyglot.js.ecmascript-version <String>`

These options control the sandboxing rules applied to evaluated JavaScript code and are set to `false` by default, unless the application was started in the Nashorn compatibility mode (`--js.nashorn-compat=true`).

Note that using `ScriptEngine` implies allowing experimental options.
This is an exhaustive list of allowed options to be passed via Bindings; in case you need to pass additional options to GraalVM JavaScript, you need to manually create a `Context` as shown below.

To set an option via `Bindings`, use `Bindings.put(<option name>, true)` **before** the engine's script context is initialized. Note that
even a call to `Bindings#get(String)` may lead to context initialization.
The following code shows how to enable `polyglot.js.allowHostAccess` via `Bindings`:
```java
ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
bindings.put("polyglot.js.allowHostAccess", true);
bindings.put("polyglot.js.allowHostClassLookup", (Predicate<String>) s -> true);
bindings.put("javaObj", new Object());
engine.eval("(javaObj instanceof Java.type('java.lang.Object'));"); // it will not work without allowHostAccess and allowHostClassLookup
```
This example will not work if the user calls, e.g., `engine.eval("var x = 1;")`, before calling `bindings.put("polyglot.js.allowHostAccess", true);`, since
any call to `eval` forces context initialization.

## Setting Options via System Properties
Options to the JavaScript engine can be set via system properties before starting the JVM by prepending `polyglot.`:
```java
java -Dpolyglot.js.ecmascript-version=2022 MyApplication
```

Or, options to the JavaScript engine can be set programmatically from within Java before creating `ScriptEngine`. This, however, only works for the options passed to the JavaScript engine (like `js.ecmascript`), not for the six options mentioned above that can be set via the `Bindings`.
Another caveat is that those system properties are shared by all concurrently executed `ScriptEngine`s.

## Manually Creating `Context` for More Flexibility
`Context` options can also be passed to `GraalJSScriptEngine` directly, via an instance of `Context.Builder`:
```java
ScriptEngine engine = GraalJSScriptEngine.create(null,
        Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowHostClassLookup(s -> true)
        .option("js.ecmascript-version", "2022"));
engine.put("javaObj", new Object());
engine.eval("(javaObj instanceof Java.type('java.lang.Object'));");
```

This allows setting all options available in GraalVM JavaScript.
It does come at the cost of a hard dependency on GraalVM JavaScript, e.g., the `GraalJSScriptEngine` and `Context` classes.

## Supported File Extensions
The GraalVM JavaScript implementation of `javax.script.ScriptEngine` supports the `js` file extension for JavaScript source files, as well as the `mjs` extension for ES modules.
