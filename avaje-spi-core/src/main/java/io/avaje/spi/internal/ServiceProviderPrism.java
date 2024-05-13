package io.avaje.spi.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/** A Prism representing a {@link io.avaje.spi.ServiceProvider @ServiceProvider} annotation. */
final class ServiceProviderPrism {
  /** store prism value of value */
  private final List<TypeMirror> _value;

  public static final String PRISM_TYPE = "io.avaje.spi.ServiceProvider";

  /**
   * Return a prism representing the {@link io.avaje.spi.ServiceProvider @ServiceProvider}
   * annotation present on the given element. similar to {@code
   * element.getAnnotation(ServiceProvider.class)} except that an instance of this class rather than
   * an instance of {@link io.avaje.spi.ServiceProvider @ServiceProvider} is returned.
   *
   * @param element element.
   * @return prism on element or null if no annotation is found.
   */
  static ServiceProviderPrism getInstanceOn(Element element) {
    final var mirror = getMirror(element);
    if (mirror == null) return null;
    return getInstance(mirror);
  }

  /**
   * Return a Optional representing a nullable {@link io.avaje.spi.ServiceProvider @ServiceProvider}
   * annotation on the given element. similar to {@link
   * element.getAnnotation(io.avaje.spi.ServiceProvider.class)} except that an Optional of this
   * class rather than an instance of {@link io.avaje.spi.ServiceProvider} is returned.
   *
   * @param element element.
   * @return prism optional for element.
   */
  static Optional<ServiceProviderPrism> getOptionalOn(Element element) {
    final var mirror = getMirror(element);
    if (mirror == null) return Optional.empty();
    return getOptional(mirror);
  }

  /**
   * Return a prism of the {@link io.avaje.spi.ServiceProvider @ServiceProvider} annotation from an
   * annotation mirror.
   *
   * @param mirror mirror.
   * @return prism for mirror or null if mirror is an incorrect type.
   */
  static ServiceProviderPrism getInstance(AnnotationMirror mirror) {
    if (mirror == null || !PRISM_TYPE.equals(mirror.getAnnotationType().toString())) return null;

    return new ServiceProviderPrism(mirror);
  }

  /**
   * Return an Optional representing a nullable {@link ServiceProviderPrism @ServiceProviderPrism}
   * from an annotation mirror. similar to {@link
   * e.getAnnotation(io.avaje.spi.ServiceProvider.class)} except that an Optional of this class
   * rather than an instance of {@link io.avaje.spi.ServiceProvider @ServiceProvider} is returned.
   *
   * @param mirror mirror.
   * @return prism optional for mirror.
   */
  static Optional<ServiceProviderPrism> getOptional(AnnotationMirror mirror) {
    if (mirror == null || !PRISM_TYPE.equals(mirror.getAnnotationType().toString()))
      return Optional.empty();

    return Optional.of(new ServiceProviderPrism(mirror));
  }

  private ServiceProviderPrism(AnnotationMirror mirror) {
    for (final ExecutableElement key : mirror.getElementValues().keySet()) {
      memberValues.put(key.getSimpleName().toString(), mirror.getElementValues().get(key));
    }
    for (final ExecutableElement member :
        ElementFilter.methodsIn(mirror.getAnnotationType().asElement().getEnclosedElements())) {
      defaults.put(member.getSimpleName().toString(), member.getDefaultValue());
    }
    _value = getArrayValues("value", TypeMirror.class);
  }

  /**
   * Returns a List&lt;TypeMirror&gt; representing the value of the {@code value()} member of the
   * Annotation.
   *
   * @see io.avaje.spi.ServiceProvider#value()
   */
  public List<TypeMirror> value() {
    return _value;
  }

  /**
   * A class whose members corespond to those of {@link
   * io.avaje.spi.ServiceProvider @ServiceProvider} but which each return the AnnotationValue
   * corresponding to that member in the model of the annotations. Returns null for defaulted
   * members. Used for Messager, so default values are not useful.
   */
  static final class Values {
    private final Map<String, AnnotationValue> values;

    private Values(Map<String, AnnotationValue> values) {
      this.values = values;
    }
    /**
     * Return the AnnotationValue corresponding to the value() member of the annotation, or null
     * when the default value is implied.
     */
    AnnotationValue value() {
      return values.get("value");
    }
  }

  private final Map<String, AnnotationValue> defaults = new HashMap<>(10);
  private final Map<String, AnnotationValue> memberValues = new HashMap<>(10);

  private <T> List<T> getArrayValues(String name, final Class<T> clazz) {
    return ServiceProviderPrism.getArrayValues(memberValues, defaults, name, clazz);
  }

  private static AnnotationMirror getMirror(Element target) {
    for (final var m : target.getAnnotationMirrors()) {
      final CharSequence mfqn =
          ((TypeElement) m.getAnnotationType().asElement()).getQualifiedName();
      if (PRISM_TYPE.contentEquals(mfqn)) return m;
    }
    return null;
  }

  private static <T> List<T> getArrayValues(
      Map<String, AnnotationValue> memberValues,
      Map<String, AnnotationValue> defaults,
      String name,
      final Class<T> clazz) {
    AnnotationValue av = memberValues.get(name);
    if (av == null) av = defaults.get(name);
    if (av == null) {
      return List.of();
    }
    if (av.getValue() instanceof List) {
      final List<T> result = new ArrayList<>();
      for (final var v : getValueAsList(av)) {
        if (clazz.isInstance(v.getValue())) {
          result.add(clazz.cast(v.getValue()));
        } else {
          return List.of();
        }
      }
      return result;
    } else {
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private static List<AnnotationValue> getValueAsList(AnnotationValue av) {
    return (List<AnnotationValue>) av.getValue();
  }
}
