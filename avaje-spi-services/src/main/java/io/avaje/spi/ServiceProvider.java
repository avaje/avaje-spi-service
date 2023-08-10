package io.avaje.spi;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation for service providers as described in {@link java.util.ServiceLoader}. The
 * annotation processor generates the configuration files that allow the annotated class to be
 * loaded with {@link java.util.ServiceLoader#load(Class)}.
 *
 * <p>The annotated class must conform to the service provider specification. Specifically, it must:
 *
 * <ul>
 *   <li>be a non-inner, non-anonymous, concrete class
 *   <li>have a publicly accessible no-arg constructor
 * </ul>
 */
@Documented
@Target(TYPE)
@Retention(SOURCE)
public @interface ServiceProvider {

  /**
   * The specific interface to generate a service registration.
   *
   * @return if none are defined the SPI interface will be inferred.
   */
  Class<?>[] value() default {};
}
