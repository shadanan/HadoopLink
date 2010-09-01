package com.wolfram.hadoop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.typedbytes.TypedBytesWritable;

import com.wolfram.jlink.Expr;

/**
 * Job driver for a Mathematica backed map-reduce job
 */
public class MathematicaJob extends Configured {

  public static final String M_PACKAGES = "wolfram.packages";

  private String jobName;
  private Job job;

  private Expr map;
  private Expr reduce;

  private List<String> inputs;
  private String output;
 
  public MathematicaJob(String jobName) {
    this.jobName = jobName;
   inputs = new ArrayList<String>();
  }

  public void setMapFunction(Expr map) {
    checkFunctionExpr(map);
    this.map = map;
  }

  public void setReduceFunction(Expr reduce) {
    checkFunctionExpr(reduce);
    this.reduce = reduce;
  }

  public void addInputPath(String input) {
    inputs.add(input);
  }

  public void setOutputPath(String output) {
    this.output = output;
  }

  public void launch() throws Exception {
    Configuration conf = getConf();
    job = new Job(conf);
    job.setJobName(jobName);
    job.setJarByClass(MathematicaJob.class);
    /* Write out map/reduce implementations to working directory */
    Path workingPath = job.getWorkingDirectory();
    FileSystem fs = FileSystem.get(conf);
    writeFunctionExpr(fs, new Path(workingPath, "mapper.m"), map);
    writeFunctionExpr(fs, new Path(workingPath, "reducer.m"), reduce);
    /* Set input paths */
    for (String input : inputs) {
      FileInputFormat.addInputPath(job, new Path(input));
    }
    /* Set output path */
    FileOutputFormat.setOutputPath(job, new Path(output));
    /* Set up classes for various job roles */
    job.setInputFormatClass(SequenceFileInputFormat.class);
    job.setMapperClass(MathematicaMapper.class);
    job.setReducerClass(MathematicaReducer.class);
    job.setOutputKeyClass(TypedBytesWritable.class);
    job.setOutputValueClass(TypedBytesWritable.class);
    job.setOutputFormatClass(SequenceFileOutputFormat.class);

    job.waitForCompletion(false);
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
