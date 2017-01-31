package randoop.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeightedListTest {

  @Test
  public void testEmptyList() {
    WeightedList wl = new WeightedList();
    assertTrue(wl.getRandomElement() == null);
  }

  // TODO fix this.
  @Test
  public void testOneElement() {
    WeightedList wl = new WeightedList();
    WeightedElement<WeightObject> expected =
        new WeightedElement<WeightObject>(new WeightObject(), 3);
    wl.add(expected);
    WeightedElement result = wl.getRandomElement();
    assertEquals(expected, result);
  }

  // TODO make new interface,
  private class WeightObject {
    public double weight = 0.0;
  }
}
