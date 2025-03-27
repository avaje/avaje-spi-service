## Fatal error compiling

If we see the following error when compiling using maven, this means that
the `maven.compiler.release` has not been specified.

```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile
(default-compile) on project insight-service: Fatal error compiling: java.lang.IllegalStateException:
Maven pom is missing maven.compiler.release.
Look to add <maven.compiler.release>21</maven.compiler.release> into the properties section.
Refer to https://github.com/avaje/avaje-spi-service/issues/47 -> [Help 1]
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
<!--          <source>11</source>-->
<!--          <target>11</target>-->
  </configuration>
</plugin>
```
