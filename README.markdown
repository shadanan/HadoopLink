# HadoopLink #

HadoopLink provides a framework for delegating the work of a
map-reduce job to Mathematica kernels running on your Hadoop cluster
and a suite of tools for working with your Hadoop cluster from a
Mathematica notebook.

## Features ##

### Distributed Filesystem Interaction ###

Wherever possible, HadoopLink provides an analogue to _Mathematica_'s filesystem interaction functions for use with the Hadoop filesystem API. These functions are compatible with HDFS, the local filesystem, Amazon S3, and any other system that can be accessed through the Hadoop filesystem API.

## How to install HadoopLink ##

Evaluate `FileNameJoin[{$UserBaseDirectory, "Applications"}]` in _Mathematica_ and unpack the HadoopLink release archive
to the indicated directory.

## How to build HadoopLink ##

Building HadoopLink requires:

* Apache Ant
* _Mathematica_ (version 7 or higher)
* Wolfram Workbench (for building the documentation notebooks)
* Hadoop version 0.20, patched to include the [typed bytes][tb] binary data support for Hadoop Streaming.

HadoopLink was developed against the Cloudera Distribution for Hadoop, version 3.

[tb]: https://issues.apache.org/jira/browse/HADOOP-1722

The following properties affect the HadoopLink ant tasks:

<dl>

<dt><code>mathematica.dir</code></dt>
<dd>The path to your local <em>Mathematica</em> installation. Can be found by evaluating the
 <code>$InstallationDirectory</code> symbol in <em>Mathematica</em>.</dd>

<dt><code>hadoop.home</code></dt>
<dd>The path to your local Hadoop installation.</dd>

<dt><code>workbench.dir</code></dt>
<dd><em>Optional</em>. The path to your Wolfram Workbench installation. If omitted, <code>skip.docbuild</code>
 will be set.</dd>

<dt><code>skip.docbuild</code></dt>
<dd><em>Optional</em>. Set this property to skip building the documentation.</dd>

</dl>

Build the HadoopLink distribution by running:

`ant -Dmathematica.dir=$MATHEMATICA_PATH -Dhadoop.home=$HADOOP_PATH -Dworkbench.dir=$WORKBENCH_PATH build`

using appropriate values for your system.

## To do ##

There are a number of areas in which HadoopLink could be improved.

- Define a Mathematica API for use in the _Mathematica_ implementation of mapper and reducer functions.
- Make sequence file export from _Mathematica_ break writes up into chunks to avoid Java out of heap errors.
- Make sequence file import compatible with all Writable subclasses in `org.apache.hadoop.io`.
- Improve error handling in DFS interaction functions.
- Switch error messages from using <code>Throw</code> to <code>Message</code>.
