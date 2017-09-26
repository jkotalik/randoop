package randoop.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import plume.SimpleLog;
import randoop.BugInRandoopException;

/**
 * A simple-to-use wrapper around {@link java.util.Random}.
 *
 * <p>It also supports logging, for debugging of apparently nondeterministic behavior.
 */
public final class Randomness {

  public static SimpleLog selectionLog = new SimpleLog(false);
  /** 0 = no output, 1 = brief output, 2 = verbose output */
  public static int verbosity = 1;

  private Randomness() {
    throw new IllegalStateException("no instances");
  }

  public static final long SEED = 0;

  /**
   * The random generator that makes random choices. (Developer note: do not declare new Random
   * objects; use this one instead).
   */
  static Random random = new Random(SEED);

  public static void setSeed(long newSeed) {
    random.setSeed(newSeed);
    totalCallsToRandom = 0;
    logSelection("[Random object]", "setSeed", newSeed);
  }

  private static Field seedField;

  static {
    try {
      seedField = Random.class.getDeclaredField("seed");
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
    seedField.setAccessible(true);
  }

  /**
   * Get the seed.
   *
   * <p>To exactly reproduce the state, the result needs to be XOR'd with 0x5DEECE66DL if passed to
   * setSeed, because setSeed internally XORs with that value.
   *
   * @return the internal random seed
   */
  public static long getSeed() {
    try {
      return ((AtomicLong) seedField.get(Randomness.random)).get();
    } catch (IllegalAccessException e) {
      throw new Error(e);
    }
  }

  /** Number of calls to the underlying Random instance that this wraps. */
  private static int totalCallsToRandom = 0;

  /** Call this before every use of Randomness.random. */
  private static void incrementCallsToRandom(String caller) {
    totalCallsToRandom++;
    if (Log.isLoggingOn()) {
      Log.logLine(
          "randoop.util.Randomness called by "
              + caller
              + ": "
              + totalCallsToRandom
              + " calls to Random so far, seed = "
              + getSeed());
    }
  }

  /**
   * Uniformly random int from [0, i)
   *
   * @param i upper bound on range for generated values
   * @return a value selected from range [0, i)
   */
  public static int nextRandomInt(int i) {
    incrementCallsToRandom("nextRandomInt");
    int value = Randomness.random.nextInt(i);
    logSelection(value, "nextRandomInt", i);
    return value;
  }

  public static <T> T randomMember(List<T> list) {
    if (list == null || list.isEmpty()) {
      throw new IllegalArgumentException("Expected non-empty list");
    }
    int position = nextRandomInt(list.size());
    logSelection(position, "randomMember", list);
    return list.get(position);
  }

  public static <T> T randomMember(SimpleList<T> list) {
    if (list == null || list.isEmpty()) {
      throw new IllegalArgumentException("Expected non-empty list");
    }
    int position = nextRandomInt(list.size());
    logSelection(position, "randomMember", list);
    return list.get(position);
  }

  /**
   * Randomly selects an element from a weighted distribution of elements.
   *
   * <p>Efficiency note: iterates through the entire list twice (once to compute interval length,
   * once to select element).
   */
  public static <T extends WeightedElement> T randomMemberWeighted(SimpleList<T> list) {

    double totalWeight = 0.0;
    for (int i = 0; i < list.size(); i++) {
      double weight = list.get(i).getWeight();
      if (weight <= 0) {
        throw new BugInRandoopException("Weight should be positive: " + weight);
      }
      totalWeight += weight;
    }

    // Select a random point in interval and find its corresponding element.
    incrementCallsToRandom("randomMemberWeighted(SimpleList)");
    double chosenPoint = Randomness.random.nextDouble() * totalWeight;
    double currentPoint = 0;
    for (int i = 0; i < list.size(); i++) {
      currentPoint += list.get(i).getWeight();
      if (currentPoint >= chosenPoint) {
        logSelection(i, "randomMemberWeighted", list);
        return list.get(i);
      }
    }
    throw new BugInRandoopException("Unable to select random member");
  }

  /**
   * Randomly selects an element from a weighted distribution of elements. These weights are with
   * respect to each other, and are not normalized. Used internally when the {@code
   * --weighted-constants} and/or {@code --weighted-sequences} options are used. Iterates through
   * the entire list once, then does a binary search to select the element.
   *
   * @param list the list of elements to select from
   * @param weights the map of elements to their weights
   * @param <T> the type of the elements in the list
   * @return a randomly selected element of type T
   */
  public static <T extends WeightedElement> T randomMemberWeighted(
      SimpleList<T> list, Map<T, Double> weights) {

    // Find interval length.
    double max = 0;
    List<Double> cumulativeWeights = new ArrayList<>();
    cumulativeWeights.add(0.0);
    for (int i = 0; i < list.size(); i++) {
      Double weight = weights.get(list.get(i));
      if (weight == null) {
        Log.logLine("randoop.util.Randomness: weight was null");
        weight = list.get(i).getWeight();
      }
      if (weight <= 0) throw new BugInRandoopException("weight was " + weight);
      cumulativeWeights.add(cumulativeWeights.get(cumulativeWeights.size() - 1) + weight);
      max += weight;
    }
    assert max > 0;

    // Select a random point in interval and find its corresponding element.
    incrementCallsToRandom("randomMemberWeighted");
    double randomPoint = Randomness.random.nextDouble() * max;
    if (selectionLog.enabled()) {
      selectionLog.log(
          "randomPoint = %s, cumulativeWeights = %s%n", randomPoint, cumulativeWeights);
    }

    assert list.size() + 1 == cumulativeWeights.size(); // because cumulative weights starts at 0

    int index = binarySearchForIndex(cumulativeWeights, randomPoint);
    if (selectionLog.enabled()) { // body is expensive
      logSelection(
          index,
          "randomMemberWeighted(List,Map)",
          String.format(
              "%n << %s%n    (class %s),%n    %s%n    (class %s, size %s)>>",
              list, list.getClass(), weights, weights.getClass(), weights.size()));
    }
    return list.get(index);
  }

  /**
   * Performs a binary search on a cumulative weight distribution and returns the corresponding
   * index i such that {@code cumulativeWeights.get(i) < point <= cumulativeWeights.get(i + 1)} for
   * {@code 0 <= i <= cumulativeWeights.size()}.
   *
   * @param cumulativeWeights the cumulative weight distribution to search through
   * @param point the value used to find the index within the cumulative weight distribution
   * @return the index corresponding to point's location in the cumulative weight distribution
   */
  private static int binarySearchForIndex(List<Double> cumulativeWeights, double point) {
    int low = 0;
    int high = cumulativeWeights.size();
    int mid = (low + high) / 2;
    while (!(cumulativeWeights.get(mid) < point && cumulativeWeights.get(mid + 1) >= point)) {
      if (cumulativeWeights.get(mid) < point) {
        low = mid;
      } else {
        high = mid;
      }
      mid = (low + high) / 2;
    }
    return mid;
  }

  /** Return a random member of the set, selected uniformly at random. */
  public static <T> T randomSetMember(Collection<T> set) {
    int setSize = set.size();
    int randIndex = Randomness.nextRandomInt(setSize);
    logSelection(randIndex, "randomSetMember", set);
    return CollectionsExt.getNthIteratedElement(set, randIndex);
  }

  /** Return true with probability {@code trueProb}, otherwise false. */
  public static boolean weightedCoinFlip(double trueProb) {
    if (trueProb < 0 || trueProb > 1) {
      throw new IllegalArgumentException("arg must be between 0 and 1.");
    }
    double falseProb = 1 - trueProb;
    incrementCallsToRandom("weightedCoinFlip");
    boolean result = Randomness.random.nextDouble() >= falseProb;
    logSelection(result, "weightedCoinFlip", trueProb);
    return result;
  }

  /** Return true or false with the given relative probabilites, which need not add to 1. */
  public static boolean randomBoolFromDistribution(double falseProb_, double trueProb_) {
    double falseProb = falseProb_ / (falseProb_ + trueProb_);
    incrementCallsToRandom("randomBoolFromDistribution");
    boolean result = Randomness.random.nextDouble() >= falseProb;
    logSelection(result, "randomBoolFromDistribution", falseProb_ + ", " + trueProb_);
    return result;
  }

  /**
   * Logs the value that was randomly selected, along with the calling method and its argument.
   *
   * @param returnValue the value randomly selected
   * @param methodName the name of the method called
   * @param argument the method argument
   */
  private static void logSelection(Object returnValue, String methodName, Object argument) {
    if (selectionLog.enabled() && verbosity > 0) {
      StackTraceElement[] trace = Thread.currentThread().getStackTrace();
      String methodWithArg = methodName;
      if (argument != null) {
        methodWithArg += "(" + toString(argument) + ")";
      }
      selectionLog.log(
          "#%d: %s => %s; seed %s; called from %s%n",
          totalCallsToRandom, methodWithArg, returnValue, getSeed(), trace[3]);
    }
  }

  /**
   * Produces a printed representation of the object, depending on the verbosity level.
   *
   * @param o the object to produce a printed representation of
   * @return a printed representation of the argument
   * @see #verbosity
   */
  private static String toString(Object o) {
    if (o instanceof Collection<?>) {
      Collection<?> coll = (Collection<?>) o;
      switch (verbosity) {
        case 1:
          return coll.getClass() + " of size " + coll.size();
        case 2:
          return coll.toString();
        default:
          throw new Error("verbosity = " + verbosity);
      }
    } else if (o instanceof SimpleList<?>) {
      SimpleList<?> sl = (SimpleList<?>) o;
      switch (verbosity) {
        case 1:
          return sl.getClass() + " of size " + sl.size();
        case 2:
          return sl.toJDKList().toString();
        default:
          throw new Error("verbosity = " + verbosity);
      }
    } else {
      return o.toString();
    }
  }
}
