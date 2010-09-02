package com.wolfram.hadoop;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.typedbytes.TypedBytesWritable;
import org.apache.hadoop.util.StringUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.MathLinkException;

public class MathematicaReducer extends
    Reducer<TypedBytesWritable, TypedBytesWritable,
    TypedBytesWritable, TypedBytesWritable> {
  private static final Log LOG = LogFactory.getLog(MathematicaReducer.class);

  private HadoopLink link;

  private TypedBytesWritable outputKey;
  private TypedBytesWritable outputValue;

  @Override
  public void setup(Context context) {
    outputKey = new TypedBytesWritable();
    outputValue = new TypedBytesWritable();
    /* Initialize a Mathematica kernel */
    try {
      Configuration conf = context.getConfiguration();
      link = new HadoopLink(conf);
      /* Set up the evaluation function for this task */
      link.defineEvaluationFunction(conf.get(MathematicaJob.REDUCER));
    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error initializing kernel for task");
    }
  }

  @Override
  public void reduce(TypedBytesWritable key,
                     Iterable<TypedBytesWritable> values,
                     Context context)
      throws IOException, InterruptedException {
    Expr k = ExprUtil.toExpr(key.getValue());
    /* TODO replace with some mechanism for passing an iterator to M-- */
    Expr vals = new Expr(ExprUtil.toSymbol("List"), new Expr[] {});
    for (TypedBytesWritable value : values) {
      Expr v = ExprUtil.toExpr(value.getValue());
      vals = vals.insert(v, -1); // append to the end of the value queue
    }
    try {
      link.evaluateKeyValuePair(k, vals);
    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      return;
    }
    Expr resultKey;
    Expr resultValue;
    while((resultKey = link.nextKey()) != null) {
      resultValue = link.nextValue();
      outputKey.setValue(ExprUtil.fromExpr(resultKey));
      outputValue.setValue(ExprUtil.fromExpr(resultValue));
      context.write(outputKey, outputValue);
    }
  }

  @Override
  public void cleanup(Context context) {
    /* Shut down Mathematica connection */
    link.close();
  }

}
