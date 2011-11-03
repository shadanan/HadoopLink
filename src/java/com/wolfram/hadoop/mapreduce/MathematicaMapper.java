package com.wolfram.hadoop.mapreduce;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.typedbytes.TypedBytesWritable;
import org.apache.hadoop.util.StringUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

public class MathematicaMapper
    extends Mapper<TypedBytesWritable, TypedBytesWritable,
                   TypedBytesWritable, TypedBytesWritable> {
  private static final Log LOG = LogFactory.getLog(MathematicaMapper.class);

  private Expr mapper;
  private KernelLink link;
  private MathematicaTask task;

  @Override
  public void setup(Context context) {
    /* Initialize a Mathematica kernel */
    try {
      Configuration conf = context.getConfiguration();
      link = MapReduceKernelLink.get(conf);

      /* Load the package file defining the mapper's dependencies */
      MapReduceKernelLink.loadPackageFromJar(link,
          conf.get(MathematicaJob.MAPPER_DEPENDENCIES));

      task = new MathematicaTask(context);

      /* Set up the evaluation function for this task */
      link.evaluate("Unique[mapfn]");
      link.waitForAnswer();
      mapper = link.getExpr();

      link.evaluate(conf.get(MathematicaJob.MAPPER));
      link.waitForAnswer();
      Expr mapFn = link.getExpr();

      link.putFunction("Set", 2);
        link.put(mapper);
        link.put(mapFn);
      link.endPacket();
      link.discardAnswer();

    } catch (MathLinkException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error initializing kernel for task");
    } catch (IOException e) {
      LOG.error(StringUtils.stringifyException(e));
      throw new RuntimeException("Error initializing kernel for task");
    }
  }

  @Override
  public void map(TypedBytesWritable key, TypedBytesWritable value,
                  Context context) throws IOException, InterruptedException {
    task.setContext(context);
    try {
      /* Evaluates this record in Mathematica, injecting a MathematicaTask
         object to wrap the context and enable communication back to Java */
      link.putFunction("MapImplementation", 4);
        link.putReference(task);
        link.put(mapper);
        link.put(key.getValue());
        link.put(value.getValue());
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
