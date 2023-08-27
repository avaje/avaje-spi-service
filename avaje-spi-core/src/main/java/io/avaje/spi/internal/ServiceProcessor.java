package io.avaje.spi.internal;

import static java.util.stream.Collectors.toSet;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
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
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.avaje.prism.GenerateUtils;

@GenerateUtils
@SupportedAnnotationTypes(ServiceProviderPrism.PRISM_TYPE)
public class ServiceProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private final Map<String, Set<String>> services = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> foundServices = new HashMap<>();

  Pattern regex = Pattern.compile("provides\\s+(.*?)\\s+with");
  private Elements elements;
  private Messager messager;

  private Types types;

  private ModuleElement moduleElement;

  @Override
  public synchronized void init(ProcessingEnvironment env) {
    super.init(env);
    this.elements = env.getElementUtils();
    this.messager = env.getMessager();
    this.types = env.getTypeUtils();
  }

  @Override
  public boolean process(Set<? extends TypeElement> tes, RoundEnvironment roundEnv) {
    final var annotated =
        roundEnv.getElementsAnnotatedWith(element(ServiceProviderPrism.PRISM_TYPE));

    // discover services from the current compilation sources
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
    findModule(tes, roundEnv);
    if (roundEnv.processingOver()) {
      write();
      validateModule();
    }
    return false;
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
    final Filer filer = processingEnv.getFiler();
    for (final var e : services.entrySet()) {
      final String contract = e.getKey();
      try {
        final FileObject f =
            filer.getResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + contract);
        final BufferedReader r =
            new BufferedReader(new InputStreamReader(f.openInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) e.getValue().add(line);
        r.close();
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

    // Write the service files
    for (final Map.Entry<String, Set<String>> e : services.entrySet()) {
      try {
        final String contract = e.getKey();
        logDebug("Writing META-INF/services/%s", contract);
        final FileObject f =
            processingEnv
                .getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + contract);
        final PrintWriter pw =
            new PrintWriter(new OutputStreamWriter(f.openOutputStream(), "UTF-8"));
        for (final String value : e.getValue()) pw.println(value);
        pw.close();
      } catch (final IOException x) {
        logError("Failed to write service definition files: %s", x);
      }
    }
  }

  private List<TypeElement> getServiceInterfaces(TypeElement type) {
    final List<TypeElement> typeElementList = new ArrayList<>();
    final var spis = ServiceProviderPrism.getInstanceOn(type).value();

    final var interfaces = type.getInterfaces();
    final boolean hasBaseClass =
        type.getSuperclass().getKind() != TypeKind.NONE && !isObject(type.getSuperclass());

    final boolean hasInterfaces = !interfaces.isEmpty();

    if (spis.isEmpty()) {
      // This inferring of the service was inspired by Pistachio, which was in turn inspired by
      // Kohsuke-Metainf
      if (hasBaseClass ^ hasInterfaces) {
        if (hasBaseClass) {
          typeElementList.add(asElement(type.getSuperclass()));
        } else {
          typeElementList.add(asElement(type.getInterfaces().get(0)));
        }
      } else {
        logError(type, "SPI type was not specified, and could not be inferred.");
      }
      return typeElementList;
    }

    for (final var m : spis) {
      if (!hasInterfaces && !hasBaseClass || !isAssignable2Interface(type, m)) {
        logError(type, "Service Provider does not extend %s", m);
      } else if (m instanceof DeclaredType) {
        typeElementList.add(asElement(m));
      } else {
        logError(type, "Invalid type specified as the Service Provider Interface");
      }
    }
    return typeElementList;
  }

  private boolean isObject(TypeMirror t) {
    if (t instanceof DeclaredType) {
      return "java.lang.Object".equals(asElement(t).getQualifiedName().toString());
    }
    return false;
  }

  private TypeElement element(String rawType) {
    return elements.getTypeElement(rawType);
  }

  private TypeElement asElement(TypeMirror returnType) {
    return (TypeElement) types.asElement(returnType);
  }
  /** Log an error message. */
  private void logError(Element e, String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
  }

  private void logError(String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
  }

  private void logWarn(Element e, String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING, String.format(msg, args), e);
  }

  private void logDebug(String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
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
      // Keep track of missing strings and their corresponding keys
      Map<String, Set<String>> missingStringsMap = new HashMap<>();
      services.forEach(
          (k, v) ->
              missingStringsMap.put(k, v.stream().map(ProcessorUtils::shortType).collect(toSet())));
      try (var inputStream =
              processingEnv
                  .getFiler()
                  .getResource(StandardLocation.SOURCE_PATH, "", "module-info.java")
                  .toUri()
                  .toURL()
                  .openStream();
          var reader = new BufferedReader(new InputStreamReader(inputStream))) {
        String line;
        String service = null;
        boolean inProvides = false;
        while ((line = reader.readLine()) != null) {

          if (line.contains("provides")) {
            inProvides = true;
            var matcher = regex.matcher(line);
            if (matcher.find()) {

              service = matcher.group(1);
            }
          }

          if (!inProvides || line.isBlank()) {
            if (line.contains("requires")
                && line.contains("io.avaje.spi")
                && !line.contains("static")) {
              logWarn(
                  moduleElement,
                  "`requires io.avaje.spi` should be `requires static io.avaje.spi;`");
            }

            continue;
          }

          processLine(line, missingStringsMap, service);

          if (line.contains(";")) {
            inProvides = false;
          }
        }

        missingStringsMap.forEach(
            (k, v) -> {
              if (!v.isEmpty()) {
                logError(
                    moduleElement,
                    "Missing `provides %s with %s;`",
                    k,
                    String.join(", ", services.get(k)));
              }
            });

      } catch (Exception e) {
        // can't read module
      }
    }
  }

  // Process a single line of input and check for missing strings
  private void processLine(
      String line, Map<String, Set<String>> missingStringsMap, String service) {
    Set<String> stringSet = missingStringsMap.get(service);
    Set<String> found = foundServices.computeIfAbsent(service, k -> new HashSet<>());

    if (!found.containsAll(stringSet)) {
      findMissingStrings(line, stringSet, found, service);
    }
    if (!foundServices.isEmpty()) {
      stringSet.removeAll(found);
    }
  }

  // Find strings from the set that are missing in the input string
  private void findMissingStrings(
      String input, Set<String> stringSet, Set<String> foundStrings, String key) {

    for (var str : stringSet) {
      if (input.contains(str)) {
        foundStrings.add(str);
      }
    }
  }
}
