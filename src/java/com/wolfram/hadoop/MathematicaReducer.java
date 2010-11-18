package com.wolfram.hadoop;

import java.io.IOException;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.typedbytes.TypedBytesWritable;
import org.apache.hadoop.util.StringUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

public class MathematicaReducer extends
    Reducer<TypedBytesWritable, TypedBytesWritable,
    TypedBytesWritable, TypedBytesWritable> {
  private static final Log LOG = LogFactory.getLog(MathematicaReducer.class);

  private Expr reducer;
  private KernelLink link;
  private MathematicaTask task;

  @Override
  public void setup(Context context) {
    /* Initialize a Mathematica kernel */
    try {
      Configuration conf = context.getConfiguration();
      link = MapReduceKernelLink.get(conf);
      MapReduceKernelLink.loadPackageFromJar(link, "reducer.m");

      task = new MathematicaTask(context);

      /* Set up the evaluation function for this task */
      link.evaluate("Unique[reducefn]");
      link.waitForAnswer();
      reducer = link.getExpr();

      link.evaluate(conf.get(MathematicaJob.REDUCER));
      link.waitForAnswer();
      Expr reduceFn = link.getExpr();

      link.putFunction("Set", 2);
        link.put(reducer);
        link.put(reduceFn);
      link.endPacket();
      link.discardAnswer();

    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error initializing kernel for task");
    }
  }

  @Override
  public void reduce(TypedBytesWritable key,
                     Iterable<TypedBytesWritable> values,
                     Context context) throws IOException, InterruptedException {
    task.setContext(context);
    ValuesIterator iter = new ValuesIterator(values.iterator());
    try {
      /* Evaluates this record in Mathematica, injecting a MathematicaTask
         object to wrap the context and enable communication back to Java */
      link.putFunction("ReduceImplementation", 4);
        link.putReference(task);
        link.put(reducer);
        link.put(key.getValue());
        link.putReference(iter);
      link.endPacket();
      link.discardAnswer();
    } catch (MathLinkException ex) {
      LOG.error(StringUtils.stringifyException(ex));
    }
  }

  @Override
  public void cleanup(Context context) {
    /* Shut down Mathematica connection */
    link.terminateKernel();
    link.close();
  }

}

class ValuesIterator implements Iterator {

  private Iterator iter;

  ValuesIterator(Iterator iter) {
    this.iter = iter;
  }

  public boolean hasNext() {
    return iter.hasNext();
  }

  public Object next() {
    return ((TypedBytesWritable) iter.next()).getValue();
  }

  public void remove() {}
}
