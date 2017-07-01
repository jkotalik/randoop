package randoop.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import plume.SimpleLog;
import randoop.BugInRandoopException;

public final class Randomness {

  public static SimpleLog selectionLog = new SimpleLog(false);

  private Randomness() {
    throw new IllegalStateException("no instances");
  }

  public static final long SEED = 0;

  /**
   * The random number used any testtime a random choice is made. (Developer note: do not declare
   * new Random objects; use this one instead).
   */
  static Random random = new Random(SEED);

  public static void reset(long newSeed) {
    random = new Random(newSeed);
    logSelection("[Random object]", "reset", newSeed);
  }

  private static int totalCallsToRandom = 0;

  /**
   * Uniformly random int from [0, i)
   *
   * @param i upper bound on range for generated values
   * @return a value selected from range [0, i)
   */
  public static int nextRandomInt(int i) {
    totalCallsToRandom++;
    if (Log.isLoggingOn()) {
      Log.logLine("randoop.util.Randomness: " + totalCallsToRandom + " calls so far.");
    }
    int value = random.nextInt(i);
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

  // Warning: iterates through the entire list twice (once to compute interval
  // length, once to select element).
  public static <T extends WeightedElement> T randomMemberWeighted(SimpleList<T> list) {

    // Find interval length.
    double max = 0;
    for (int i = 0; i < list.size(); i++) {
      double weight = list.get(i).getWeight();
      if (weight <= 0) throw new BugInRandoopException("weight was " + weight);
      max += weight;
    }
    assert max > 0;

    // Select a random point in interval and find its corresponding element.
    totalCallsToRandom++;
    if (Log.isLoggingOn()) {
      Log.logLine("randoop.util.Randomness: " + totalCallsToRandom + " calls so far.");
    }
    double randomPoint = Randomness.random.nextDouble() * max;
    double currentPoint = 0;
    for (int i = 0; i < list.size(); i++) {
      currentPoint += list.get(i).getWeight();
      if (currentPoint >= randomPoint) {
        logSelection(i, "randomMemberWeighted", list);
        return list.get(i);
      }
    }
    throw new BugInRandoopException();
  }

  /**
   * Randomly selects an element from a weighted distribution of elements. These weights are with
   * respect to each other, and are not normalized. Used internally when the <code>
   * --weighted-constants</code> and/or <code>--weighted-sequences</code> options are used. Iterates
   * through the entire list once, then does a binary search to select the element.
   *
   * @param list the list of elements to select from
   * @param weights the map of elements to their weights
   * @param <T> the type of the elements in the list
   * @return a randomly selected element of type T
   */
  public static <T extends WeightedElement> T randomMemberWeighted(
      SimpleList<T> list, Map<WeightedElement, Double> weights) {

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
    totalCallsToRandom++;
    if (Log.isLoggingOn()) {
      Log.logLine("randoop.util.Randomness: " + totalCallsToRandom + " calls so far.");
    }
    double randomPoint = Randomness.random.nextDouble() * max;

    assert list.size() + 1 == cumulativeWeights.size(); // because cumulative weights starts at 0

    int index = binarySearchForIndex(cumulativeWeights, randomPoint);
    logSelection(index, "randomMemberWeighted(List,Map)", list);
    return list.get(index);
  }

  /**
   * Performs a binary search on a cumulative weight distribution and returns the corresponding
   * index such that {@code cumulativeWeights.get(i) < point <= cumulativeWeights.get(i + 1) for
   * index 0 <= i <= cumulativeWeights.size()},
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

  public static <T> T randomSetMember(Collection<T> set) {
    int setSize = set.size();
    int randIndex = Randomness.nextRandomInt(setSize);
    logSelection(randIndex, "randomSetMember", "collection of size " + setSize);
    return CollectionsExt.getNthIteratedElement(set, randIndex);
  }

  public static boolean weightedCoinFlip(double trueProb) {
    if (trueProb < 0 || trueProb > 1) {
      throw new IllegalArgumentException("arg must be between 0 and 1.");
    }
    double falseProb = 1 - trueProb;
    totalCallsToRandom++;
    if (Log.isLoggingOn()) {
      Log.logLine("randoop.util.Randomness: " + totalCallsToRandom + " calls so far.");
    }
    boolean result = Randomness.random.nextDouble() >= falseProb;
    logSelection(result, "weightedCoinFlip", trueProb);
    return result;
  }

  public static boolean randomBoolFromDistribution(double falseProb_, double trueProb_) {
    double falseProb = falseProb_ / (falseProb_ + trueProb_);
    totalCallsToRandom++;
    if (Log.isLoggingOn()) {
      Log.logLine("randoop.util.Randomness: " + totalCallsToRandom + " calls so far.");
    }
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
    if (selectionLog.enabled()) {
      StackTraceElement[] trace = Thread.currentThread().getStackTrace();
      String methodWithArg = methodName;
      if (argument != null) {
        methodWithArg += "(" + argument + ")";
      }
      selectionLog.log("%s => %s; called from %s%n", methodName, returnValue, trace[2]);
    }
  }

  /**
   * Logs the value that was randomly selected, along with the calling method and its lint argument.
   *
   * @param returnValue the value randomly selected
   * @param methodName the name of the method called
   * @param argList the method argument, which is a list
   */
  private static void logSelection(Object returnValue, String methodName, List<?> argList) {
    if (selectionLog.enabled()) {
      logSelection(returnValue, methodName, "list of length " + argList.size());
    }
  }
}
