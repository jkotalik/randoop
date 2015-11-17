package randoop.test;

import java.io.InputStream;
import java.util.List;

import randoop.experiments.RandomWalkGenerator;
import randoop.main.GenInputsAbstract;
import randoop.operation.Operation;
import randoop.util.Reflection;

public class NaivePerformanceTest extends AbstractPerformanceTest {

  @Override
  void execute() {
    String resourcename = "resources/java.util.classlist.java1.6.txt";

    InputStream classStream =
      ForwardExplorerPerformanceTest.class.getResourceAsStream(resourcename);

    List<Operation> model =
      Reflection.getStatements(Reflection.loadClassesFromStream(classStream, resourcename),null);
    System.out.println("done creating model.");
    GenInputsAbstract.dontexecute = true; // FIXME make this an instance field?
    GenInputsAbstract.debug_checks = false;
    RandomWalkGenerator explorer = new RandomWalkGenerator(model, Long.MAX_VALUE, 100000, null, null, null, null);
    explorer.explore();
  }

  @Override
  int expectedTimeMillis() {
    return 16000;
  }

}
