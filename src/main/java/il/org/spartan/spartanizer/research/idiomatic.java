package il.org.spartan.spartanizer.research;

import static il.org.spartan.azzert.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

import org.junit.*;

import il.org.spartan.*;

/** An empty <code><b>enum</b></code> with a variety of <code>public
 * static</code> utility functions of reasonably wide use.
 * @author Yossi Gil <code><yossi.gil [at] gmail.com></code>
 * @since 2013/07/01 */
public interface idiomatic {
  /** Single quote: */
  final String QUOTE = "'";
  /** an evaluating trigger */
  final Trigger eval = new Trigger() {
    @Override public <T> T eval(final Supplier<T> ¢) {
      return ¢.get();
    }
  };
  /** an ignoring trigger */
  final Trigger ignore = new Trigger() {
    @Override public <T> T eval(@SuppressWarnings("unused") final Supplier<T> __) {
      return null;
    }
  };

  /** @param <T> JD
   * @param $ result
   * @return an identical supplier which is also a {@link Holder} */
  static <T> Holder<T> eval(final Supplier<T> $) {
    return () -> $.get();
  }

  /** @param condition JD
   * @return */
  /** <code>incase</code>
   * @param <T> JD
   * @param condition
   * @param t JD
   * @return T */
  static <T> T incase(final boolean condition, final T t) {
    return condition ? t : null;
  }

  /** A filter, which prints an appropriate log message and returns null in case
   * of {@link Exception} thrown by {@link Producer#λ()}
   * @param <T> JD
   * @param $ JD
   * @return result of invoking the parameter, or <code><b>null</b></code> if an
   *         exception occurred. */
  static <T> T katching(final Producer<T> $) {
    try {
      return $.λ();
    } catch (final Exception ¢) {
      ¢.printStackTrace();
      return null;
    }
  }

  /** Quote a given {@link String}
   * @param $ some {@link String} to be quoted
   * @return parameter, quoted */
  static String quote(final String $) {
    return $ != null ? QUOTE + $ + QUOTE : "<null reference>";
  }

  /** @param ¢ JD
   * @return an identical runnable which is also a {@link Runner} */
  static Runner run(final Runnable ¢) {
    return new Runner(¢);
  }

  /** @param <T> JD
   * @param ¢ JD
   * @return Yielder<T> */
  static <T> Storer<T> take(final T ¢) {
    return new Storer<>(¢);
  }

  /** @param condition JD
   * @return */
  static Trigger unless(final boolean condition) {
    return when(!condition);
  }

  /** @param <T> JD
   * @param condition when should the action take place
   * @param t JD
   * @return non-boolean parameter, in case the boolean parameter is true, or
   *         null, otherwise */
  static <T> T unless(final boolean condition, final T t) {
    return incase(!condition, t);
  }

  /** @param condition JD
   * @return */
  static Trigger when(final boolean condition) {
    return condition ? eval : ignore;
  }

  static <T> Storer<T> defolt(final T ¢) {
    return new Storer<>(¢);
  }

  /** Supplier with {@link #when(boolean)} method
   * @param <T> JD
   * @author Yossi Gil <Yossi.Gil@GMail.COM>
   * @since 2016 */
  interface Holder<T> extends Supplier<T> {
    /** Return value when condition is <code><b>true</b></code>
     * @param unless condition on which value is returned
     * @return {@link #get()} when the parameter is <code><b>true</b></code> ,
     *         otherwise code><b>null</b></code>. */
    default T unless(final boolean unless) {
      return when(!unless);
    }

    /** Return value when condition is <code><b>true</b></code>
     * @return {@link #get()} when the parameter is <code><b>true</b></code> ,
     *         otherwise code><b>null</b></code>.
     * @param when condition on which value is returned */
    default T when(final boolean when) {
      return when ? get() : null;
    }

    default T to(final T ¢) {
      return get() == null ? ¢ : get();
    }
  }

  /** A class which is just like {@link Supplier} , except that it uses the
   * shorter name ( {@link #λ()} and that it allows for {@link Exception} s to
   * be thrown by the getters.
   * @author Yossi Gil
   * @param < T > JD
   * @since 2016` */
  @FunctionalInterface interface Producer<T> {
    /** @return next value provided by this instance
     * @throws Exception JD */
    T λ() throws Exception;
  }

  /** Evaluate a {@link Runnable} when a condition applies or unless a condition
   * applies.
   * @author Yossi Gil <Yossi.Gil@GMail.COM>
   * @since 2016 */
  static class Runner implements Runnable {
    private final Runnable run;

    /** Instantiates this class.
     * @param run JD */
    Runner(final Runnable run) {
      this.run = run;
    }

    @Override public void run() {
      run.run();
    }

    /** <code>unless</code>
     * @param unless condition n which execution occurs. */
    public void unless(final boolean unless) {
      when(!unless);
    }

    void when(final boolean when) {
      if (when)
        run();
    }
  }

  /** Store a value to be returned with {@link #get()} function
   * @param <T> JD
   * @author Yossi Gil <Yossi.Gil@GMail.COM>
   * @since 2016 */
  static class Storer<T> implements Holder<T> {
    /** */
    final T inner;

    /** Instantiates this class.
     * @param inner JD */
    Storer(final T inner) {
      this.inner = inner;
    }

    /** see @see java.util.function.Supplier#get() (auto-generated) */
    @Override public T get() {
      return inner;
    }
  }

  @SuppressWarnings({ "javadoc", "static-method" }) static class TEST {
    @Test public void use0() {
      azzert.notNull(new Storer<>(this));
    }

    @Test public void use08() {
      azzert.isNull(unless(true).eval(() -> new Object()));
    }

    @Test public void use09() {
      azzert.notNull(unless(false).eval(() -> new Object()));
    }

    @Test public void use1() {
      azzert.notNull(new Storer<>(this));
      new Storer<>(this).when(true);
    }

    @Test public void use10() {
      azzert.notNull(when(true).eval(() -> new Object()));
    }

    @Test public void use11() {
      azzert.isNull(when(false).eval(() -> new Object()));
    }

    @Test public void use2() {
      azzert.notNull(take(this));
      azzert.isNull(take(this).when(false));
    }

    @Test public void use3() {
      azzert.that(take(this).when(true), is(this));
    }

    @Test public void use4() {
      azzert.isNull(take(this).when(false));
    }

    @Test public void use5() {
      azzert.that(take(this).unless(false), is(this));
    }

    @Test public void use6() {
      azzert.isNull(take(this).unless(true));
    }

    @Test public void use7() {
      azzert.isNull(take(this).unless(true));
      azzert.isNull(take(null).unless(true));
      azzert.isNull(take(null).unless(false));
    }
  }

  /** @author Yossi Gil <Yossi.Gil@GMail.COM>
   * @since 2016 */
  interface Trigger {
    /** @param <T> JD
     * @param t JD
     * @return */
    <T> T eval(final Supplier<T> t);

    /** @param <T> JD
     * @param $ JD
     * @return */
    default <T> T eval(final T $) {
      return eval(() -> $);
    }
  }

  static <T> With<T> map(Collection<T> ¢) {
    return new With<>(¢);
  }

  class With<T> {
    final Collection<T> collection;

    public With(final Collection<T> collection) {
      this.collection = collection;
    }

    @SuppressWarnings("unchecked") public <R> Collection<R> with(final Function<? super T, ? extends R> mapper) {
      Collection<R> $ = null;
      try {
        $ = collection.getClass().getConstructor().newInstance();
        for (T ¢ : collection)
          $.add(mapper.apply(¢));
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
          | SecurityException x) {
        x.printStackTrace();
      }
      return $;
    }
  }
}