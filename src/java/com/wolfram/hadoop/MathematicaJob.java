package com.wolfram.hadoop;

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
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
  public static final String MAPPER = "wolfram.mapper.function";
  public static final String REDUCER = "wolfram.reducer.function";

  private String jobName;
  private Job job;

  private Expr map = null;
  private Expr reduce = null;

  private List<String> inputs;
  private String output = null;
 
  public MathematicaJob(String jobName) {
    this.jobName = jobName;
    inputs = new ArrayList<String>();
  }

  public void setMapFunction(Expr map) {
    this.map = map;
  }

  public void setReduceFunction(Expr reduce) {
    this.reduce = reduce;
  }

  public void addInputPath(String input) {
    inputs.add(input);
  }

  public void setOutputPath(String output) {
    this.output = output;
  }

  public Job launch(Configuration conf) throws Exception {
    assert inputs.size() != 0;
    assert output != null;
    assert map != null;
    assert reduce != null;

    conf.set(MAPPER, map.toString());
    conf.set(REDUCER, reduce.toString());

    job = new Job(conf);
    job.setJobName(jobName);
    job.setJarByClass(MathematicaJob.class);

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
    job.submit();
    return job;
  }
}
