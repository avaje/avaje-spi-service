[![Build](https://github.com/avaje/avaje-spi-service/actions/workflows/build.yml/badge.svg)](https://github.com/avaje/avaje-spi-service/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/avaje/avaje-spi-service/blob/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.avaje/avaje-spi-service.svg?label=Maven%20Central)](https://mvnrepository.com/artifact/io.avaje/avaje-spi-service)
[![Discord](https://img.shields.io/discord/1074074312421683250?color=%237289da&label=discord)](https://discord.gg/Qcqf9R27BR)
# avaje-spi-service
Uses Annotation processing to automatically add `META-INF/services` entries for classes and validate `module-info.java` files.

## Usage
### 1. Add dependency:

#### Maven:
```xml
<dependency>
  <groupId>io.avaje</groupId>
  <artifactId>avaje-spi-service</artifactId>
  <version>${spi.version}</version>
  <optional>true</optional>
  <scope>provided</scope>
</dependency>
```

#### Gradle:
```kotlin
compileOnly("io.avaje:avaje-spi-service:${spi.version}")
annotationProcessor("io.avaje:avaje-spi-service:${spi.version}")
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

## Related Works
- [Pistachio](https://github.com/jstachio/pistachio)

