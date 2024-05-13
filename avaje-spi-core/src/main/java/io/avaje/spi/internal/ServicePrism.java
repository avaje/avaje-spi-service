package io.avaje.spi.internal;

import java.util.Optional;

import javax.annotation.processing.Generated;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** A Prism representing a {@link io.avaje.spi.Service @Service} annotation. */
@Generated("avaje-prism-generator")
final class ServicePrism {
  public static final String PRISM_TYPE = "io.avaje.spi.Service";

  /**
   * Returns true if the mirror is an instance of {@link io.avaje.spi.Service @Service} is present
   * on the element, else false.
   *
   * @param mirror mirror.
   * @return true if prism is present.
   */
  static boolean isInstance(AnnotationMirror mirror) {
    return getInstance(mirror) != null;
  }

  /**
   * Returns true if {@link io.avaje.spi.Service @Service} is present on the element, else false.
   *
   * @param element element.
   * @return true if annotation is present on the element.
   */
  static boolean isPresent(Element element) {
    return getInstanceOn(element) != null;
  }

  /**
   * Return a prism representing the {@link io.avaje.spi.Service @Service} annotation present on the
   * given element. similar to {@code element.getAnnotation(Service.class)} except that an instance
   * of this class rather than an instance of {@link io.avaje.spi.Service @Service} is returned.
   *
   * @param element element.
   * @return prism on element or null if no annotation is found.
   */
  static ServicePrism getInstanceOn(Element element) {
    final var mirror = getMirror(element);
    if (mirror == null) return null;
    return getInstance(mirror);
  }

  /**
   * Return a Optional representing a nullable {@link io.avaje.spi.Service @Service} annotation on
   * the given element. similar to {@code element.getAnnotation(io.avaje.spi.Service.class)} except
   * that an Optional of this class rather than an instance of {@link io.avaje.spi.Service} is
   * returned.
   *
   * @param element element.
   * @return prism optional for element.
   */
  static Optional<ServicePrism> getOptionalOn(Element element) {
    final var mirror = getMirror(element);
    if (mirror == null) return Optional.empty();
    return getOptional(mirror);
  }

  /**
   * Return a prism of the {@link io.avaje.spi.Service @Service} annotation from an annotation
   * mirror.
   *
   * @param mirror mirror.
   * @return prism for mirror or null if mirror is an incorrect type.
   */
  static ServicePrism getInstance(AnnotationMirror mirror) {
    if (mirror == null || !PRISM_TYPE.equals(mirror.getAnnotationType().toString())) return null;

    return new ServicePrism(mirror);
  }

  /**
   * Return an Optional representing a nullable {@link ServicePrism @ServicePrism} from an
   * annotation mirror. similar to {@code e.getAnnotation(io.avaje.spi.Service.class)} except that
   * an Optional of this class rather than an instance of {@link io.avaje.spi.Service @Service} is
   * returned.
   *
   * @param mirror mirror.
   * @return prism optional for mirror.
   */
  static Optional<ServicePrism> getOptional(AnnotationMirror mirror) {
    if (mirror == null || !PRISM_TYPE.equals(mirror.getAnnotationType().toString()))
      return Optional.empty();

    return Optional.of(new ServicePrism(mirror));
  }

  private ServicePrism(AnnotationMirror mirror) {
    this.mirror = mirror;
    this.isValid = valid;
  }

  /**
   * Determine whether the underlying AnnotationMirror has no errors. True if the underlying
   * AnnotationMirror has no errors. When true is returned, none of the methods will return null.
   * When false is returned, a least one member will either return null, or another prism that is
   * not valid.
   */
  final boolean isValid;

  /**
   * The underlying AnnotationMirror of the annotation represented by this Prism. Primarily intended
   * to support using Messager.
   */
  final AnnotationMirror mirror;

  private final boolean valid = true;

  private static AnnotationMirror getMirror(Element target) {
    for (final var m : target.getAnnotationMirrors()) {
      final CharSequence mfqn =
          ((TypeElement) m.getAnnotationType().asElement()).getQualifiedName();
      if (PRISM_TYPE.contentEquals(mfqn)) return m;
    }
    return null;
  }
}
