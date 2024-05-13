package io.avaje.spi;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a class/interface as service type (not an implementation).
 *
 * <p>Use when the type is meant to be the default inferred type.
 *
 * <pre>{@code
 * @Service
 * sealed class A permits B {
 *   ...
 * }
 *
 * non-sealed class B extends A {
 *   ...
 * }
 *
 * //the default inferred SPI is A instead of B
 * &#64;ServiceProvider
 * class C extends B {
 *   ...
 * }
 *
 * }</pre>
 */
@Target(TYPE)
@Retention(CLASS)
public @interface Service {}
