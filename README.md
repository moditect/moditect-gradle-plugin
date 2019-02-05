[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/moditect/moditect-gradle-plugin/blob/master/LICENSE)
[![Build Status](https://img.shields.io/travis/moditect/moditect-gradle-plugin/master.svg?label=Build)](https://travis-ci.org/moditect/moditect-gradle-plugin)

# ModiTect Gradle Plugin

This plugin, which is available in the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/org.moditect.gradleplugin), brings [ModiTect](https://github.com/moditect/moditect/blob/master/README.md)'s functionality to Gradle.


* [Tasks](#tasks)
    * [generateModuleInfo](#generateModuleInfo)
    * [addMainModuleInfo](#addMainModuleInfo)
    * [addDependenciesModuleInfo](#addDependenciesModuleInfo)
    * [createRuntimeImage](#createRuntimeImage)

* [Examples](#examples)
    * [Hibernate Validator](#hibernate-validator)
    * [Undertow](#undertow)
    * [Vert.x](#vertx)

When applying the plugin, the first three tasks are automatically integrated into the build lifecycle and will be executed during every build.
The last one, which creates a custom runtime image of your application, should be started explicitly when needed.

The tasks can be configured in the `moditect` block, as shown below:

```
moditect {
    generateModuleInfo {
        ...
    }
    addMainModuleInfo {
        ...
    }
    addDependenciesModuleInfo {
        ...
    }
    createRuntimeImage {
        ...
    }
}
```

## Tasks

### generateModuleInfo
 This task lets you create module-info.java descriptors for given artifacts.
 An example configuration is shown below:

```
generateModuleInfo {
    jdepsExtraArgs = ['-q']
    outputDirectory = file("$buildDir/generated-sources/modules")
    modules {
        module {
            artifact 'com.example:example-core:1.0.0.Final'
            moduleInfo {
                name = 'com.example.core'
                exports = '''
                    !com.example.core.internal*;
                    *;
                '''
                requires = '''
                    static com.some.optional.dependency;
                    !com.excluded.dependency;
                    *;
                '''
                uses = '''
                    com.example.SomeService;
                '''
                addServiceUses = true
            }
        }
        ...
    }
}
```

This will generate a module descriptor at _build/generated-sources/modules/com.example.core/module-info.java_.

* jdepsExtraArgs : A list of arguments passed to the _jdeps_ invocation for creating a "candidate descriptor" (optional)

* outputDirectory: Directory in which the module descriptors will be generated (optional; default value: _build/generated-sources/modules_)

* For each module to be processed, the following configuration options exist:

  * `artifact`: The coordinates of the artifact for which a descriptor should be generated, using one of the existing [dependency notations](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html#N17046) (required)
  * `additionalDependencies`: Additional artifacts to be processed; useful if the main artifact depends on code from another artifact but doesn't declare a dependency to that one (optional)
  * `moduleInfo`: Allows fine-grained configuration of the generated module
descriptor (optional); has the following sub-elements:
    - `name`: Name to be used within the descriptor; if not given the name
will be derived from the JAR name as per the naming rules for automatic modules
(optional)
    - `open`: Whether the descriptor should be an open module or not (optional, defaults
to `false`)
    - `exports`: List of name patterns for describing the exported packages of the module,
separated by ";". Patterns can be inclusive or exclusive (starting with "!") and may
contain the "\*" as a wildcard. Inclusive patterns may be qualified exports ("to xyz").
For each package from the module, the given patterns are processed in the order they
are given. As soon a package is matched by an inclusive pattern, the package will be
added to the list of exported packages and no further patterns will be applied. As soon
as a package is matched by an exclusive pattern, this package will not be added to the
list of exported packages and no further patterns will be applied.
(optional; the default value is "\*;", i.e. all packages will be exported)
    - `opens`: List of name patterns for describing the open packages of the module,
separated by ";". Patterns can be inclusive or exclusive (starting with "!") and may
contain the "\*" as a wildcard. Inclusive patterns may be qualified exports ("to xyz").
For each package from the module, the given patterns are processed in the order they
are given. As soon a package is matched by an inclusive pattern, the package will be
added to the list of open packages and no further patterns will be applied. As soon
as a package is matched by an exclusive pattern, this package will not be added to the
list of open packages and no further patterns will be applied.
(optional; the default value is "!\*;", i.e. no packages will be opened)
    - `requires`: List of name patterns for describing the dependences of the module,
  based on the automatically determined dependences.
Patterns are inclusive or exclusive (starting with "!") and may contain the "\*" character as a wildcard.
Inclusive patterns may contain the `static` and `transitive` modifiers, in which case those modifiers will override the modifiers of the automatically determined dependence. For each of the automatically determined dependences of the module, the given patterns are processed in the order they are given.
As soon as a dependence is matched by a pattern, the dependence will be added to the list of dependences (if the pattern is inclusive) or the dependence will be filtered out (for exclusive patterns) and no further patterns will be applied. Usually, only a few dependences will be given explicitly in order to override their modifiers, followed by a `*;` pattern to add all remaining automatically determined dependences.
    - `addServiceUses`: If `true`, the given artifact will be scanned for usages of `ServiceLoader#load()` and if usages passing a class literal are found (`load( MyService.class )`), an equivalent `uses()` clause will be added to the generated descriptor; usages of `load()` where a non-literal class object is passed, are ignored (optional, defaults to `false`)
    - `uses`: List of names of used services, separated by ";" only required if `addServiceUses` cannot be used due to dynamic invocations of `ServiceLoader#load()`, i.e. no class literal is passed (optional)


### addMainModuleInfo
This task lets you add a module descriptor to the project JAR.
An example configuration is shown below:
```
addMainModuleInfo {
    version = project.version
    jvmVersio = 11
    overwriteExistingFiles = false
    jdepsExtraArgs = ['-q']
    module {
        mainClass = 'com.example.MainApp'
        moduleInfo {
            name = 'com.example'
            exports = '''
                !com.example.internal.*;
                *;
            '''
        }
    }
}
```
The optional `jvmVersion` element allows to define which JVM version the module descriptor should target
(leveraging the concept of multi-release JARs).
When defined, the module descriptor will be put into `META-INF/versions/${jvmVersion}`.
The value must be `9` or greater.
The special value `base` (the default) can be used to add the descriptor to the root of the final JAR.
Putting the descriptor under `META-INF/versions` can help to increase compatibility with older libraries scanning class files that may fail when encountering the `module-info.class` file
(as chances are lower that such tool will look for class files under `META-INF/versions/...`).

The `jdepsExtraArgs` option can be used to specify a list of arguments passed to the _jdeps_ invocation for creating a "candidate descriptor".

The following configuration options exist for the `module` block:

* `moduleInfoSource`: Inline representation of a module-info.java descriptor (optional; either this or `moduleInfoFile` or `moduleInfo` must be given)
* `moduleInfoFile`: Path to a module-info.java descriptor
(optional; either this or `moduleInfoSource` or `moduleInfo` must be given)
* `moduleInfo`: A `moduleInfo` configuration as used with the `generateModuleInfo` task (optional; either this or `moduleInfoSource` or `moduleInfoFile` must be given)
* `mainClass`: The fully-qualified name of the main class to be added to the module descriptor (optional)


### addDependenciesModuleInfo
This task lets you add module descriptors to existing JAR files.
An example configuration is shown below:
```
addDependenciesModuleInfo {
    jdepsExtraArgs = ['-q']
    outputDirectory = file("$buildDir/modules")
    modules {
        module {
            artifact 'com.example:example-core:1.0.0.Final'
            moduleInfoSource = '''
                module com.example.core {
                    requires java.logging;
                    exports com.example.api;
                    provides com.example.api.SomeService
                        with com.example.internal.SomeServiceImpl;
                }
            '''
        }
        ...
    }
}
```
For each module to be processed, the following configuration options exist:

* `artifact`: The coordinates of the artifact for which a descriptor should be generated, using one of the existing [dependency notations](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html#N17046) (required)
* `moduleInfoSource`: Inline representation of a module-info.java descriptor
(optional; either this or `moduleInfoFile` or `moduleInfo` must be given)
* `moduleInfoFile`: Path to a module-info.java descriptor
(optional; either this or `moduleInfoSource` or `moduleInfo` must be given)
* `moduleInfo`: A `moduleInfo` configuration as used with the `generateModuleInfo` task (optional; either this or `moduleInfoSource` or `moduleInfoFile` must be given)
* `mainClass`: The fully-qualified name of the main class to be added to the module descriptor (optional)
* `version`: The version to be added to the module descriptor; if not given and
`artifact` is given, the artifact's version will be used; otherwise no version will be added (optional)

The modularized JARs can be found in the folder given via `outputDirectory`.
The `jdepsExtraArgs` option can be used to specify a list of arguments passed to the _jdeps_ invocation for creating a "candidate descriptor".


### createRuntimeImage
This task lets you create a modular runtime image (see [JEP 220](http://openjdk.java.net/jeps/220)).
An example configuration is shown below:

```
createRuntimeImage {
    jdkHome = '/usr/lib/jvm/jdk_x64_linux_hotspot_11_28'
    outputDirectory = file("$buildDir/jlink-image")
    modulePath = [file("$buildDir/modules")]
    modules = ['com.example.module1', 'com.example.module2']
    excludedResources = ['glob:/com.example/**']
    launcher {
        name = 'helloWorld'
        module = 'com.example.module1'
    }
    compression = 2
    stripDebug = true
}
```
The following configuration options exist:

* `modulePath`: One or more directories with modules to be considered for
creating the image (required); the `jmods` directory of the current JVM will be
added implicitly, so it doesn't have to be given here
* `modules`: The module(s) to be used as the root for resolving the modules to
be added to the image (required)
* `outputDirectory`: Directory in which the runtime image should be created
(required)
* `launcher`: file name and main module for creating a launcher file (optional)
* `stripDebug` whether to strip debug symbols or not (optional, defaults to `false`)
* `excludedResources` list of patterns for excluding matching resources from the created runtime image
* `jdkHome`: the path to the JDK whose jmod files will be used when creating the runtime image (optional; if not given the JDK running the
current build will be used).
* `ignoreSigningInformation`: Suppresses a fatal error when signed modular JARs are linked in the runtime image. The signature-related files of the signed modular JARs aren’t copied to the runtime image.

Once the image has been created, it can be executed by running:

```
./<outputDirectory>/bin/java --module com.example
```

Or, if a launcher has been configured:

```
./<outputDirectory>/bin/<launcherName>
```

## Examples

### Hibernate Validator

To create the modular runtime image execute:
```
cd integrationtest/hibernate-validator
../../gradlew clean createRuntime
```

After that, you can run the modular runtime image by executing:
```
build/image/bin/validationTest
```


### Undertow

To create the modular runtime image execute:
```
cd integrationtest/undertow
../../gradlew clean createRuntime
```

After that, you can run the modular runtime image by executing:
```
build/image/bin/helloWorld
```

Then visit [http://localhost:8080/?name=YourName](http://localhost:8080/?name=YourName) in your browser for the canonical "Hello World" example.

You can change the port on which the server is listening by setting the value of the environment variable `HELLO_SERVER_PORT`.

### Vert.x

To create the modular runtime image execute:
```
cd integrationtest/vert.x
../../gradlew clean createRuntime
```

After that, you can run the modular runtime image by executing:
```
build/jlink-image/bin/helloWorld
```

Then visit [http://localhost:8080/?name=YourName](http://localhost:8080/?name=YourName) in your browser for the canonical "Hello World" example.

You can change the port on which the server is listening by setting the value of the environment variable `HELLO_SERVER_PORT`.
