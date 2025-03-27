[![Build](https://github.com/avaje/avaje-spi-service/actions/workflows/build.yml/badge.svg)](https://github.com/avaje/avaje-spi-service/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/avaje/avaje-spi-service/blob/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.avaje/avaje-spi-service.svg?label=Maven%20Central)](https://mvnrepository.com/artifact/io.avaje/avaje-spi-service)
[![Discord](https://img.shields.io/discord/1074074312421683250?color=%237289da&label=discord)](https://discord.gg/Qcqf9R27BR)
# avaje-spi-service
Uses Annotation processing to automatically add `META-INF/services` entries for classes and validate `module-info.java` files.

## Usage
### 1. Add dependency:
```xml
<dependency>
  <groupId>io.avaje</groupId>
  <artifactId>avaje-spi-service</artifactId>
  <version>${spi.version}</version>
  <optional>true</optional>
  <scope>provided</scope>
</dependency>
```

When working with Java modules you need to add the annotation module as a static dependency.
```java
module my.module {
  requires static io.avaje.spi;
}
```
### 2. Add `@ServiceProvider`

On classes that you'd like registered, put the `@ServiceProvider` annotation. As long as you only have one interface or one superclass, that type is assumed to be the spi interface. So given the example below:
```java
@ServiceProvider
public class MyProvider implements SomeSPI {
  ...
}
```
You get the `META-INF/services/com.example.SomeSPI` file whose content is `org.acme.MyProvider`.

If you have multiple interfaces and/or base type, the library cannot infer the contract type. In such a case, specify the contract type explicitly by giving it to `@ServiceProvider` like this:

```java
@ServiceProvider(SomeSPI.class)
public class MyExtendedProvider extends AbstractSet implements Comparable, Serializable, SomeSPI {
  ...
}
```

### 3. `module-info` validation
For modular projects, the processor will throw a compile error describing what `provides` statements you have missed. So if you define the SPI like the the previous steps, and have a module setup like the following:
```java
module my.module {

  requires static io.avaje.spi;

}
```
You'll get the following compile error:
```
 Compilation failure /src/main/java/module-info.java:[1,1]
 Missing `provides SomeSPI with MyProvider, MyExtendedProvider;`
```


## Fatal error compiling

If we see the following error when compiling using maven, this means that
the `maven.compiler.release` has not been specified.

```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile
(default-compile) on project insight-service: Fatal error compiling: java.lang.IllegalStateException:
Maven pom is missing maven.compiler.release.
Look to add <maven.compiler.release>21</maven.compiler.release> into the properties section.
Refer to https://github.com/avaje/avaje-spi-service/wiki/Compile-Error -> [Help 1]
```

This can occur when the `maven-compiler-plugin` is not configured with a **_release_** version
(or source & target version).

So this is missing:
```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
</properties>
```

or these ...
```xml
<properties>
  <maven.compiler.source>21</maven.compiler.source>
  <maven.compiler.target>21</maven.compiler.target>
</properties>
```
or specify it in the configuration of the `maven-compiler-plugin` like:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>3.14.0</version>
  <configuration>
    <release>21</release>
<!--   <source>11</source>-->
<!--   <target>11</target>-->
  </configuration>
</plugin>
```

## Related Works
- [Pistachio](https://github.com/jstachio/pistachio)

