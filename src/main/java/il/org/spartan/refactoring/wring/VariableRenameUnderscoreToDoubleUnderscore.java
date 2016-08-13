package il.org.spartan.refactoring.wring;

import org.eclipse.jdt.core.dom.*;

import il.org.spartan.refactoring.preferences.PluginPreferencesResources.*;

/** Replaces name of variables named "_" into "__"
 * @author Ori Roth
 * @param <N> either SingleVariableDeclaration or VariableDeclarationFragment
 * @since 2016/05/08 */
public class VariableRenameUnderscoreToDoubleUnderscore<N extends VariableDeclaration> extends VariableChangeName<N> {
  @Override boolean change(final N n) {
    return "_".equals(n.getName().toString());
  }
  @Override String description(@SuppressWarnings("unused") final N __) {
    return "Use double underscore instead a single underscore";
  }
  @Override SimpleName replacement(final N n) {
    return n.getAST().newSimpleName("__");
  }
  @Override WringGroup wringGroup() {
    return WringGroup.RENAME_PARAMETERS;
  }
}