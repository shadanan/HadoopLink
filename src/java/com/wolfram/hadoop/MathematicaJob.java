package com.wolfram.hadoop;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;

import com.wolfram.jlink.Expr;

/**
 * Job driver for a Mathematica backed map-reduce job
 */
public class MathematicaJob extends Configured {

  public static final String M_PACKAGES = "wolfram.packages";

  private Job job;

  public MathematicaJob(Expr map, Expr reduce) throws IOException {
    checkFunctionExpr(map);
    checkFunctionExpr(reduce);

    Configuration conf = getConf();
    job = new Job(conf);
    Path workingPath = job.getWorkingDirectory();
    FileSystem fs = FileSystem.get(conf);
    writeFunctionExpr(fs, new Path(workingPath, "mapper.m"), map);
    writeFunctionExpr(fs, new Path(workingPath, "reducer.m"), reduce);

  }

  private void writeFunctionExpr(FileSystem fs, Path file, Expr expr)
      throws IOException {
    FSDataOutputStream f = fs.create(file);
    f.writeChars(expr.toString());
    f.close();
  }

  private void checkFunctionExpr(Expr expr) {
    if (!expr.head().equals("Function")) {
      String error = String.format("%s is not a Function", expr);
      throw new IllegalArgumentException(error);
    }
  }
}
