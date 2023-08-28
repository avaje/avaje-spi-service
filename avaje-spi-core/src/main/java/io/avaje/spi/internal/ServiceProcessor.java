package io.avaje.spi.internal;

import static java.util.stream.Collectors.toMap;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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
      try (final var file =
              filer
                  .getResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + contract)
                  .openInputStream();
          final var buffer =
              new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8)); ) {

        String line;
        while ((line = buffer.readLine()) != null) {
          e.getValue().add(line);
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

    // Write the service files
    for (final Map.Entry<String, Set<String>> e : services.entrySet()) {
      try {
        final String contract = e.getKey();
        logDebug("Writing META-INF/services/%s", contract);
        final FileObject file =
            processingEnv
                .getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + contract);
        final PrintWriter pw =
            new PrintWriter(
                new OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8));
        for (final String value : e.getValue()) {
          pw.println(value);
        }
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

    for (final var spiMirror : spis) {
      if (!hasInterfaces && !hasBaseClass || !isAssignable2Interface(type, spiMirror)) {
        logError(type, "Service Provider does not extend %s", spiMirror);
      } else if (spiMirror instanceof DeclaredType) {
        typeElementList.add(asElement(spiMirror));
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
    messager.printMessage(Diagnostic.Kind.WARNING, String.format(msg, args), e);
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
              missingStringsMap.put(
                  ProcessorUtils.shortType(k).replace("$", "."),
                  v.stream().map(ProcessorUtils::shortType).collect(toSet())));

      try (var inputStream =
              processingEnv
                  .getFiler()
                  .getResource(StandardLocation.SOURCE_PATH, "", "module-info.java")
                  .toUri()
                  .toURL()
                  .openStream();
          var reader = new BufferedReader(new InputStreamReader(inputStream))) {
        ModuleReader.read(missingStringsMap, reader);
        if (ModuleReader.staticWarning()) {
          logWarn(
              moduleElement, "`requires io.avaje.spi` should be `requires static io.avaje.spi;`");
        }
        logModuleError(missingStringsMap);

      } catch (Exception e) {
        // can't read module
        e.printStackTrace();
      }
    }
  }

  private void logModuleError(Map<String, Set<String>> missingStringsMap) {
    final Map<String, String> shortQualifiedMap =
        services.keySet().stream()
            .collect(toMap(s -> ProcessorUtils.shortType(s).replace("$", "."), s -> s));
    missingStringsMap.forEach(
        (k, v) -> {
          if (!v.isEmpty()) {
            logError(
                moduleElement,
                "Missing `provides %s with %s;`",
                k,
                String.join(", ", services.get(shortQualifiedMap.get(k))));
          }
        });
  }
}
