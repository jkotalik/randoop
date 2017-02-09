package randoop.util;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeightBalancedTreeTest {
  public final double epsilon = .01;

  @Test
  public void testEmptyList() {
    WeightedBalancedTree<Integer> wl = new WeightedBalancedTree<>();
    assertTrue(wl.getRandomElement() == null);
  }

  // TODO fix this.
  @Test
  public void testOneElement() {
    WeightedBalancedTree<Integer> wl = new WeightedBalancedTree<>();
    WeightedElement<Integer> expected = new WeightedElement<Integer>(new Integer(1), 3);
    wl.add(expected);
    WeightedElement<Integer> result = wl.getRandomElement();
    assertEquals(expected, result);
  }

  @Test
  public void testMutlipleElements() {
    // Create a list of weighted elements.
    WeightedBalancedTree<Integer> list = new WeightedBalancedTree<>();
    int sumOfAllWeights = 0;
    for (int i = 1; i < 10; i++) {
      int weight = i;
      list.add(new WeightedElement<Integer>(new Integer(1), i));
      sumOfAllWeights += weight;
    }

    Map<Double, Integer> weightToTimesSelected = new LinkedHashMap<>();
    int totalSelections = 0;

    // Select lots of times.
    for (int i = 0; i < 100000; i++) {
      WeightedElement<Integer> w = list.getRandomElement();
      Integer timesSelected = weightToTimesSelected.get(w.getWeight());
      if (timesSelected == null) timesSelected = 0;
      weightToTimesSelected.put(w.getWeight(), timesSelected + 1);
      totalSelections++;
    }

    // Check that elements were selected the rightEdge number of times.
    for (Map.Entry<Double, Integer> e : weightToTimesSelected.entrySet()) {
      double actualRatio = e.getValue() / (double) totalSelections;
      double expectedRatio = e.getKey() / (double) sumOfAllWeights;
      assertTrue(Math.abs(actualRatio - expectedRatio) < epsilon);
    }
  }
}