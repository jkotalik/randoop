package randoop.plugin.internal.ui.views;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view
 * shows data obtained from the model. The sample creates a dummy model on the
 * fly, but a real implementation would connect to the model available either in
 * this or another plug-in (e.g. the workspace). The view is connected to the
 * model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be
 * presented in the view. Each view can present the same model objects using
 * different labels and icons, if needed. Alternatively, a single label provider
 * can be shared between views in order to ensure that objects of the same type
 * are presented in the same way everywhere.
 * <p>
 */

public class TestGeneratorViewPart extends ViewPart {
  /**
   * The ID of the view as specified by the extension.
   */
  public static final String ID = "randoop.plugin.ui.views.TestGeneratorViewPart"; //$NON-NLS-1$

  private Composite fParent;
  private RandoopProgressBar fProgressBar;

  /**
   * The constructor.
   */
  public TestGeneratorViewPart() {

  }

  /**
   * This is a callback that will allow us to create the viewer and initialize
   * it.
   */
  @Override
  public void createPartControl(Composite parent) {
    fParent = parent;

    Composite composite = new Composite(fParent, SWT.NONE);
    GridLayout layout = new GridLayout();
    composite.setLayout(layout);

    fProgressBar = new RandoopProgressBar(composite);
    fProgressBar.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
        | GridData.HORIZONTAL_ALIGN_FILL));
  }

  public RandoopProgressBar getProgressBar() {
    return fProgressBar;
  }

  private void showMessage(String message) {
    MessageDialog.openInformation(fParent.getShell(), "Randoop View", message);
  }

  /**
   * Passing the focus request to the viewer's control.
   */
  @Override
  public void setFocus() {
    // viewer.getControl().setFocus();
  }
}