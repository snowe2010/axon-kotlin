[![Travis branch](https://img.shields.io/travis/snowe2010/axon-kotlin/master.svg?style=flat-square)](https://travis-ci.com/snowe2010/axon-kotlin)
![Maven Central](https://img.shields.io/maven-central/v/com.tylerthrailkill/axon-kotlin-test.svg?style=flat-square)

# axon-kotlin
Extensions to the Axon Framework 

A set of extensions to the Axon Framework making it easier to use in kotlin.

Currently only covers test functions, but will eventually cover all of Axon. 

Also includes a DSL for easier writing FixtureConfiguration tests. 

## Documentation

Docs are available at [https://snowe2010.github.io/axon-kotlin/axon-kotlin/](https://snowe2010.github.io/axon-kotlin/axon-kotlin/)

## Install

### Maven
```xml
<dependency>
    <groupId>com.tylerthrailkill</groupId>
    <artifactId>axon-kotlin-test</artifactId>
    <version>${axon-kotlin.version}</version>
</dependency>
```

### Gradle
```groovy
compile "com.tylerthrailkill:axon-kotlin-test:$axonKotlinVersion"
```

## Extensions

## TODO

### DSL 

* All fixture tests
* Nice error messages when unary plus isn't used when it should be
* Allow restarting dsl from arbitrary points in the chain, e.g. if you save the fixtureExecutionResult to a 
  variable, then you should be able to invoke a new block on it and continue using the dsl

### Extensions

