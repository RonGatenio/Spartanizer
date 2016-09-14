package il.org.spartan.spartanizer.wring;

import static il.org.spartan.spartanizer.ast.step.*;

import java.util.*;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.*;
import org.eclipse.text.edits.*;

import il.org.spartan.spartanizer.ast.*;
import il.org.spartan.spartanizer.engine.*;
import il.org.spartan.spartanizer.wring.dispatch.*;
import il.org.spartan.spartanizer.wring.strategies.*;

/** Remove blocks that include only variable declerations: <br/>
 * For example, remove the block : </br>
 * <br/>
 * <code> {int a=0;} </code> </br>
 * @author Dor Ma'ayan
 * @since 2016-09-11 */
public class CastBlockSingletonVariableDefinition extends Wring<Block> implements Kind.Collapse {
  @Override public String description() {
    return "remove the block";
  }

  @Override public String description(final Block n) {
    return "remove the block: " + n;
  }

  @Override public Rewrite make(final Block n) {
    final List<Statement> ss = statements(n);
    if (ss.isEmpty())
      return null;
    for (final Statement ¢ : ss)
      if (!iz.variableDeclarationStatement(¢))
        return null;
    return new Rewrite(description(), n) {
      @Override public void go(final ASTRewrite r, final TextEditGroup g) {
        for (final Statement ¢ : ss)
          r.remove(¢, g);
      }
    };
  }
}