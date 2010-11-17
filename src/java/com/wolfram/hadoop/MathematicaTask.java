package com.wolfram.hadoop;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;
import org.apache.hadoop.typedbytes.TypedBytesWritable;

import com.wolfram.jlink.Expr;

import static com.wolfram.hadoop.ExprUtil.*;

public class MathematicaTask {

  private static final Log LOG = LogFactory.getLog(MathematicaTask.class);

  private static final String DEFAULT_COUNTER_GROUP = "Mathematica";

  private TaskInputOutputContext context;

  private TypedBytesWritable key;
  private TypedBytesWritable value;

  public MathematicaTask() {
    key = new TypedBytesWritable();
    value = new TypedBytesWritable();
  }

  public void setContext(TaskInputOutputContext context) {
    this.context = context;
  }

  public void write(Expr k, Expr v) {
    key.setValue(fromExpr(k));
    value.setValue(fromExpr(v));
    context.write(key, value);
  }

  public void log(String message) {
    LOG.info(message);
  }

  public void incrementCounter(String name, long n) {
    incrementCounter(DEFAULT_COUNTER_GROUP, name, n);
  }

  public void incrementCounter(String group, String name, long n) {
    Counter counter = context.getCounter(group, name);
    counter.increment(n);
  }
}