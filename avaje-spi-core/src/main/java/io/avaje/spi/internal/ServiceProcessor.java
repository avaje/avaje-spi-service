package io.avaje.spi.internal;

import static io.avaje.spi.internal.APContext.*;
import static java.util.stream.Collectors.joining;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.StandardLocation;

import io.avaje.prism.GenerateAPContext;
import io.avaje.prism.GenerateModuleInfoReader;
import io.avaje.prism.GenerateUtils;

@GenerateUtils
@GenerateAPContext
@SuppressWarnings("exports")
@GenerateModuleInfoReader
@SupportedAnnotationTypes({
  ServiceProviderPrism.PRISM_TYPE,
  // makes the processor automatically run if any of the other avaje processors are active
  // so that automatic spi module validation happens
  "io.avaje.inject.spi.Generated",
  "io.avaje.jsonb.spi.Generated",
  "io.avaje.http.api.Client",
  "io.avaje.http.api.Controller",
  "io.avaje.recordbuilder.Generated",
  "io.avaje.prism.GenerateAPContext",
  "io.avaje.validation.spi.Generated",
  "javax.annotation.processing.Generated",
  "javax.annotation.processing.SupportedAnnotationTypes",
  "javax.annotation.processing.SupportedOptions",
  "javax.annotation.processing.SupportedSourceVersion"
})
public class ServiceProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {

    return SourceVersion.latestSupported();
  }

  private final Map<String, Set<String>> services = new ConcurrentHashMap<>();

  private Elements elements;

  private Types types;

  private ModuleElement moduleElement;

  private Path servicesDirectory;

  private Path generatedSpisDir;

  @Override
  public synchronized void init(ProcessingEnvironment env) {
    super.init(env);
    this.elements = env.getElementUtils();
    this.types = env.getTypeUtils();
    APContext.init(env);

    final var filer = env.getFiler();
    try {
      final var uri =
          filer
              .createResource(
                  StandardLocation.CLASS_OUTPUT, "", "META-INF/services/spi-service-locator")
              .toUri();
      this.servicesDirectory = Path.of(uri).getParent();
      this.generatedSpisDir =
          Path.of(
              URI.create(
                  uri.toString()
                      .replace(
                          "META-INF/services/spi-service-locator", "META-INF/generated-services")));
    } catch (IOException e) {
      // not an issue worth failing over
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> tes, RoundEnvironment roundEnv) {

    final var annotated =
        Optional.ofNullable(typeElement(ServiceProviderPrism.PRISM_TYPE))
            .map(roundEnv::getElementsAnnotatedWith)
            .orElseGet(Set::of);

    // discover services from the current compilation sources
    processSpis(annotated);

    findModule(tes, roundEnv);
    if (roundEnv.processingOver()) {
      //load generated service files into main services
      var generatedSpis = loadMetaInfServices(generatedSpisDir);
      Utils.mergeServices(generatedSpis, services);
      write();
      validateModule();
    }
    return false;
  }

  private void processSpis(final Collection<? extends Element> annotated) {
    for (final var type : ElementFilter.typesIn(annotated)) {
      validate(type);

      final List<TypeElement> contracts = getServiceInterfaces(type);
      if (contracts.isEmpty()) {
        logError(type, "Service Providers must implement an SPI interface");
      }
      for (final TypeElement contract : contracts) {
        final String cn = elements.getBinaryName(contract).toString();
        final Set<String> v = services.computeIfAbsent(cn, k -> new TreeSet<>());

        v.add(elements.getBinaryName(type).toString());
      }
    }
  }

  private void validate(final TypeElement type) {
    final var mods = type.getModifiers();
    if (!mods.contains(Modifier.PUBLIC)
        || type.getEnclosingElement().getKind() == ElementKind.CLASS
            && !mods.contains(Modifier.STATIC)) {
      logError(type, "A Service Provider must be a public class or a public static nested class");
    }
    final var noPublicConstructor =
        ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
            .filter(e -> e.getParameters().isEmpty())
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .findAny()
            .isEmpty();
    if (noPublicConstructor) {
      logError(type, "A Service Provider must have a public no-args constructor");
    }
  }

  private void write() {
    // Read the existing service files
    var allServices = loadMetaInfServices(servicesDirectory);

    // add loaded services without messing with other annotation processors' service generation
    allServices.forEach(
        (key, value) ->
            services.computeIfPresent(
                key,
                (k, v) -> {
                  v.addAll(value);
                  return v;
                }));

    // Write the service files
    for (final var e : services.entrySet()) {
      final String contract = e.getKey();
      logNote("Writing META-INF/services/%s", contract);
      try (final var file =
              processingEnv
                  .getFiler()
                  .createResource(
                      StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + contract)
                  .openOutputStream();
          final var pw = new PrintWriter(new OutputStreamWriter(file, StandardCharsets.UTF_8)); ) {

        for (final String value : e.getValue()) {
          pw.println(value);
        }
      } catch (final IOException x) {
        logError("Failed to write service definition files: %s", x);
      }
    }

    // merge all services for module validation
    Utils.mergeServices(allServices, services);
  }

  private Map<String, Set<String>> loadMetaInfServices(Path servicesDirectory) {
    var allServices = new HashMap<String, Set<String>>();
    if (servicesDirectory == null) {
      return allServices;
    }

    // Read the existing service files
    try (var servicePaths = Files.walk(servicesDirectory, 1).skip(1)) {
      Iterable<Path> pathIterable = servicePaths::iterator;
      for (var servicePath : pathIterable) {
        final var contract = Utils.fqnFromBinaryType(servicePath.getFileName().toString());
        if (APContext.typeElement(contract) == null) {
          continue;
        }
        var impls = allServices.computeIfAbsent(contract, k -> new TreeSet<>());

        try (final var file = servicePath.toUri().toURL().openStream();
            final var buffer = new BufferedReader(new InputStreamReader(file)); ) {

          String line;
          while ((line = buffer.readLine()) != null) {
            line.replaceAll("\\s", "").replace(",", "\n").lines().forEach(impls::add);
          }
        } catch (final FileNotFoundException | java.nio.file.NoSuchFileException x) {
          // missing and thus not created yet
        } catch (final IOException x) {
          logError(
              "Failed to load existing service definition file. SPI: "
                  + contract
                  + " exception: "
                  + x);
        }
      }
    } catch (NoSuchFileException e) {
      logNote("No service definition file found");
    } catch (Exception e) {
      logError("Failed to load service definition file" + e);
    }
    return allServices;
  }

  private List<TypeElement> getServiceInterfaces(TypeElement type) {
    final List<TypeElement> typeElementList = new ArrayList<>();
    final var spis = ServiceProviderPrism.getInstanceOn(type).value();

    final var interfaces = type.getInterfaces();
    final boolean hasBaseClass =
        type.getSuperclass().getKind() != TypeKind.NONE && !isObject(type.getSuperclass());

    final boolean hasInterfaces = !interfaces.isEmpty();

    if (spis.isEmpty()) {
      if (checkSPI(type.asType(), typeElementList)) {
        return typeElementList;
      }
      // This inferring of the service was inspired by Pistachio, which was in turn inspired by
      // Kohsuke-Metainf
      if (hasBaseClass ^ hasInterfaces) {
        if (hasBaseClass) {
          typeElementList.add(asTypeElement(type.getSuperclass()));
        } else {
          typeElementList.add(asTypeElement(type.getInterfaces().get(0)));
        }
      } else {
        logError(type, "SPI type was not specified, and could not be inferred.");
      }
      return typeElementList;
    }

    for (final var spiMirror : spis) {
      if (!hasInterfaces && !hasBaseClass || !isAssignable2Interface(type, spiMirror)) {
        logError(type, "Service Provider does not extend %s", spiMirror);
      } else if (spiMirror instanceof DeclaredType) {
        typeElementList.add(asTypeElement(spiMirror));
      } else {
        logError(type, "Invalid type specified as the Service Provider Interface");
      }
    }
    return typeElementList;
  }

  // if a @Service Annotation is present on a superclass/interface, use that as the inferred service
  // type
  private boolean checkSPI(TypeMirror typeMirror, final List<TypeElement> typeElementList) {
    var type = asTypeElement(typeMirror);
    if (type == null) {
      return false;
    }
    if (ServicePrism.isPresent(type)) {
      typeElementList.add(type);
      return true;
    }
    final List<TypeMirror> supers = new ArrayList<>();
    supers.add(type.getSuperclass());
    supers.addAll(type.getInterfaces());
    for (var aSuper : supers) {
      if (checkSPI(aSuper, typeElementList)) {
        return true;
      }
    }
    return false;
  }

  private boolean isObject(TypeMirror t) {
    if (t instanceof DeclaredType) {
      return "java.lang.Object".equals(asTypeElement(t).getQualifiedName().toString());
    }
    return false;
  }

  private boolean isAssignable2Interface(Element type, TypeMirror superType) {
    return Optional.ofNullable(type).stream()
        .flatMap(this::superTypes)
        .anyMatch(superType.toString()::equals);
  }

  private Stream<String> superTypes(Element element) {
    return types.directSupertypes(element.asType()).stream()
        .filter(type -> !type.toString().contains("java.lang.Object"))
        .map(superType -> (TypeElement) types.asElement(superType))
        .flatMap(e -> Stream.concat(superTypes(e), Stream.of(e)))
        .map(Object::toString);
  }

  ModuleElement findModule(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (this.moduleElement == null) {
      moduleElement =
          annotations.stream()
              .map(roundEnv::getElementsAnnotatedWith)
              .flatMap(Collection::stream)
              .findAny()
              .map(this::getModuleElement)
              .orElseThrow();
    }
    return moduleElement;
  }

  ModuleElement getModuleElement(Element e) {
    if (e == null || e instanceof ModuleElement) {
      return (ModuleElement) e;
    }
    return getModuleElement(e.getEnclosingElement());
  }

  void validateModule() {
    if (moduleElement != null && !moduleElement.isUnnamed()) {
      // Keep track of missing services and their impls
      var moduleReader = new ModuleReader(services);
      try (var reader = getModuleInfoReader()) {
        moduleReader.read(reader, moduleElement);
        if (moduleReader.staticWarning()) {
          logError(moduleElement, "`requires io.avaje.spi` should be `requires static io.avaje.spi;`");
        }
        if (moduleReader.coreWarning()) {
          logWarn(moduleElement, "io.avaje.spi.core should not be used directly");
        }
        if (!buildPluginAvailable()) {
          logModuleError(moduleReader);
        }

      } catch (Exception e) {
        // can't read module, not a critical issue
      }
    }
  }

  private void logModuleError(ModuleReader moduleReader) {
    moduleReader
        .missing()
        .forEach(
            (k, v) -> {
              if (!v.isEmpty()) {
                var contract =
                    services.keySet().stream()
                        .filter(s -> s.replace("$", ".").equals(k.replace("$", ".")))
                        .findAny()
                        .orElseThrow();
                var missingImpls =
                    services.get(contract).stream()
                        .map(Utils::fqnFromBinaryType)
                        .collect(joining(", "));

                logError(
                    moduleElement,
                    "Missing `provides %s with %s;`",
                    contract,
                    missingImpls);
              }
            });
  }

  private static boolean buildPluginAvailable() {
    return resource("target/avaje-plugin-exists.txt", "/target/classes")
        || resource("build/avaje-plugin-exists.txt", "/build/classes/java/main");
  }

  private static boolean resource(String relativeName, String replace) {
    try (var inputStream =
        new URI(
                filer()
                    .getResource(StandardLocation.CLASS_OUTPUT, "", relativeName)
                    .toUri()
                    .toString()
                    .replace(replace, ""))
            .toURL()
            .openStream()) {

      return inputStream.available() > 0;
    } catch (IOException | URISyntaxException e) {
      return false;
    }
  }
}
