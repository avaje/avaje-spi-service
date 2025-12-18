package io.avaje.spi.internal;

import static io.avaje.spi.internal.APContext.*;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.StandardLocation;

import io.avaje.prism.GenerateAPContext;
import io.avaje.prism.GenerateUtils;

@GenerateUtils
@GenerateAPContext
@SuppressWarnings("exports")
@SupportedOptions("buildPlugin")
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
  "io.ebean.typequery.Generated",
  "javax.annotation.processing.Generated",
  "javax.annotation.processing.SupportedAnnotationTypes",
  "javax.annotation.processing.SupportedOptions",
  "javax.annotation.processing.SupportedSourceVersion"
})
public class ServiceProcessor extends AbstractProcessor {

  private static final String INJECT_EXTENSION = "io.avaje.inject.spi.InjectExtension";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private static final Map<String, String> EXEMPT_SERVICES_MAP =
      Map.of(
          "avaje-inject-generator",
          INJECT_EXTENSION,
          "avaje-validator-generator",
          "io.avaje.validation.spi.ValidationExtension",
          "avaje-jsonb-generator",
          "io.avaje.jsonb.spi.JsonbExtension",
          "avaje-http-client-generator",
          "io.avaje.http.client.HttpClient.GeneratedComponent");

  private static final Map<String, String> VALID_AVAJE_SPI =
      Map.of(
          "io.avaje.config",
          "io.avaje.config.ConfigExtension",
          "io.avaje.inject.spi.AvajeModule",
          INJECT_EXTENSION,
          "io.avaje.inject.spi.InjectPlugin",
          INJECT_EXTENSION,
          "io.avaje.inject.spi.ModuleOrdering",
          INJECT_EXTENSION,
          "io.avaje.inject.spi.ConfigPropertyPlugin",
          INJECT_EXTENSION,
          "io.avaje.jsonb",
          "io.avaje.jsonb.spi.JsonbExtension",
          "io.avaje.validation",
          "io.avaje.validation.spi.ValidationExtension");

  private static final Set<String> EXEMPT_SERVICES = new HashSet<>();

  private final Map<String, Set<String>> services = new ConcurrentHashMap<>();

  private Elements elements;

  private Types types;

  private ModuleElement moduleElement;

  private Path servicesDirectory;

  @Override
  public synchronized void init(ProcessingEnvironment env) {
    super.init(env);
    this.elements = env.getElementUtils();
    this.types = env.getTypeUtils();
    APContext.init(env);
    final var filer = env.getFiler();
    try {
      final var uri = filer
        .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/spi-service-locator")
        .toUri();
      this.servicesDirectory = Path.of(uri).getParent();

      // write a note in target so that other apts can know spi is running
      var file = APContext.getBuildResource("avaje-processors.txt");
      var addition = new StringBuilder();
      //if file exists, dedup and append current processor
      if (file.toFile().exists()) {
        var result =
          Stream.concat(Files.lines(file), Stream.of("avaje-spi-core"))
            .distinct()
            .collect(joining("\n"));
        addition.append(result);
      } else {
        addition.append("avaje-spi-core");
      }
      Files.writeString(file, addition.toString(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      PomPluginWriter.addPlugin2Pom();
    } catch (IOException e) {
      // not an issue worth failing over
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> tes, RoundEnvironment roundEnv) {
    if (roundEnv.errorRaised()) {
      return false;
    }
    APContext.setProjectModuleElement(tes, roundEnv);
    final var annotated =
      Optional.ofNullable(typeElement(ServiceProviderPrism.PRISM_TYPE))
        .map(roundEnv::getElementsAnnotatedWith)
        .orElseGet(Set::of);

    // discover services from the current compilation sources
    processSpis(annotated);

    findModule(tes, roundEnv);
    if (roundEnv.processingOver()) {
      loadExemptService();
      write();
      validateCorrectAvajeServices();
      validateModule();
    }
    return false;
  }

  private void validateCorrectAvajeServices() {
    services.entrySet().stream()
      .filter(e -> e.getKey().startsWith("io.avaje") && !VALID_AVAJE_SPI.containsValue(e.getKey()))
      .forEach(e -> {
        var key = e.getKey();
        VALID_AVAJE_SPI.entrySet().stream()
          .filter(spi -> key.contains(spi.getKey()))
          .findAny()
          .ifPresent(spi ->
            logError(
              "Invalid spi entry META-INF/services/%s."
                + "\n All classes of this type should be registered in META-INF/services/%s",
              key, spi.getValue()));
      });
  }

  private void loadExemptService() {
    try (var lines = Files.lines(APContext.getBuildResource("avaje-processors.txt"))) {
      lines
        .filter(EXEMPT_SERVICES_MAP::containsKey)
        .forEach(p -> EXEMPT_SERVICES.add(EXEMPT_SERVICES_MAP.get(p)));
    } catch (IOException e) {
      // not worth failing
    }
  }

  private void processSpis(final Collection<? extends Element> annotated) {
    for (final var type : ElementFilter.typesIn(annotated)) {
      Element methodSpi =
          ElementFilter.methodsIn(type.getEnclosedElements()).stream()
              .filter(
                  m ->
                      m.getSimpleName().contentEquals("provider")
                          && m.getModifiers().contains(Modifier.STATIC)
                          && m.getModifiers().contains(Modifier.PUBLIC)
                          && m.getParameters().isEmpty())
              .findAny()
              .map(Element.class::cast)
              .orElse(type);

      validate(methodSpi);
      final List<TypeElement> contracts =
          getServiceInterfaces(
              methodSpi instanceof TypeElement
                  ? type
                  : APContext.asTypeElement(((ExecutableElement) methodSpi).getReturnType()),
              ServiceProviderPrism.getInstanceOn(type).value());
      if (contracts.isEmpty()) {
        logError(type, "Service Providers must implement an SPI interface, or provide the SPI via a public static provider() method.");
      }
      for (final TypeElement contract : contracts) {
        final String cn = elements.getBinaryName(contract).toString();
        final Set<String> v = services.computeIfAbsent(cn, k -> new TreeSet<>());

        v.add(elements.getBinaryName(type).toString());
      }
    }
  }

  private void validate(final Element element) {

    var mods = element.getModifiers();
    var kind = element.getKind();
    boolean isPublic = mods.contains(Modifier.PUBLIC);
    boolean isMethod = kind == ElementKind.METHOD;
    boolean isStatic = mods.contains(Modifier.STATIC);

    if (!isPublic) {
      logError(
          element,
          "SPI provider must be public.",
          element.getSimpleName());
      return;
    }

    if (element instanceof TypeElement) {
      validateConstructor(element);
      if (((TypeElement) element).getNestingKind().isNested() && !isStatic) {
        logError(element, "Nested SPI provider must be static.", element.getSimpleName());
        return;
      }
    }
    var current = element.getEnclosingElement();

    while (current != null && !(current instanceof PackageElement)) {
      mods = current.getModifiers();
      kind = current.getKind();
      isPublic = mods.contains(Modifier.PUBLIC);
      isStatic = mods.contains(Modifier.STATIC);

      boolean isTopLevel = (current.getEnclosingElement() instanceof PackageElement);

      if (!isPublic) {
        logError(element, "SPI provider classes must be public.");
        return;
      }

      if (!isMethod && !isStatic && !isTopLevel) {
        logError(element, "Nested SPI provider classes must be static.");
        return;
      }

      current = current.getEnclosingElement();
    }
  }

  private void validateConstructor(Element type) {
    boolean hasPublicNoArg =
        ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
            .anyMatch(
                c -> c.getParameters().isEmpty() && c.getModifiers().contains(Modifier.PUBLIC));

    if (!hasPublicNoArg) {
      logError(type, "A Service Provider must have a public no-args constructor.");
    }
  }

  private void write() {
    // Read the existing service files
    var allServices = loadMetaInfServices(servicesDirectory);

    // add loaded services without messing with other annotation processors' service generation
    allServices.forEach((key, value) ->
      services.computeIfPresent(key, (k, v) -> {
        v.addAll(value);
        return v;
      }));

    // Write the service files
    for (final var e : services.entrySet()) {
      final String contract = e.getKey();
      if (EXEMPT_SERVICES.contains(contract)) {
        continue;
      }
      logNote("Writing META-INF/services/%s", contract);
      try (final var file =
             processingEnv
               .getFiler()
               .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + contract)
               .openOutputStream();
           final var pw = new PrintWriter(new OutputStreamWriter(file, StandardCharsets.UTF_8));) {

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
            line.replaceAll("\\s", "").replace(',', '\n').lines().forEach(impls::add);
          }
        } catch (final FileNotFoundException | java.nio.file.NoSuchFileException x) {
          // missing and thus not created yet
        } catch (final IOException x) {
          logError("Failed to load existing service definition file. SPI: " + contract + " exception: " + x);
        }
      }
    } catch (NoSuchFileException e) {
      logNote("No service definition file found");
    } catch (Exception e) {
      logError("Failed to load service definition file" + e);
    }
    return allServices;
  }

  private List<TypeElement> getServiceInterfaces(TypeElement type, List<TypeMirror> spis) {
    final List<TypeElement> typeElementList = new ArrayList<>();
    final var interfaces = type.getInterfaces();
    final boolean hasBaseClass = type.getSuperclass().getKind() != TypeKind.NONE && !isObject(type.getSuperclass());
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

  // if a @Service Annotation is present on a superclass/interface, use that as the inferred service type
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

  void findModule(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (this.moduleElement == null) {
      moduleElement =
        annotations.stream()
          .map(roundEnv::getElementsAnnotatedWith)
          .flatMap(Collection::stream)
          .findAny()
          .map(this::getModuleElement)
          .orElseThrow(() -> {
            int javaVersion = processingEnv.getSourceVersion().ordinal();
            String msg = String.format("Java release version is %s, please set maven.compiler.release to 11 or higher", javaVersion);
            return new IllegalStateException(msg);
          });
    }
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
      APContext.moduleInfoReader().ifPresent(reader -> {
        moduleReader.read(reader);
        if (moduleReader.staticWarning()) {
          logError(moduleElement, "`requires io.avaje.spi` should be `requires static io.avaje.spi;`");
        }
        if (moduleReader.coreWarning()) {
          logWarn(moduleElement, "io.avaje.spi.core should not be used directly");
        }
        if (!buildPluginAvailable() && !APContext.isTestCompilation()) {
          logModuleError(moduleReader);
        }
      });
    }
  }

  private void logModuleError(ModuleReader moduleReader) {
    moduleReader.missing().forEach((k, v) -> {
      v.removeIf(this::isNotSameModule);
      if (!v.isEmpty()) {
        var contract =
          services.keySet().stream()
            .filter(s -> s.replace('$', '.').equals(k.replace('$', '.')))
            .findAny()
            .orElseThrow();
        var missingImpls =
          services.get(contract).stream()
            .filter(not(this::isNotSameModule))
            .map(Utils::fqnFromBinaryType)
            .collect(joining(", "));

        logError(moduleElement, "Missing `provides %s with %s;`", Utils.fqnFromBinaryType(contract), missingImpls);
      }
    });
  }

  private boolean isNotSameModule(String type) {
    var element = typeElement(Utils.fqnFromBinaryType(type));
    return element == null
        || !elements
            .getModuleOf(element)
            .getSimpleName()
            .contentEquals(moduleElement.getSimpleName());
  }

  private static boolean buildPluginAvailable() {
    return resource("avaje-plugin-exists.txt");
  }

  private static boolean resource(String relativeName) {
    try {
      return APContext.getBuildResource(relativeName).toFile().exists();
    } catch (final Exception e) {
      return false;
    }
  }
}
