BeginPackage["HadoopLink`", {"JLink`"}]

HadoopLink::usage = "HadoopLink is an object that represents a Hadoop \
installation and configuration."

OpenHadoopLink::usage = "OpenHadoopLink[path] creates a HadoopLink object \
from the provided Hadoop installation path."

DFSFileNames::usage = "DFSFileNames[link] lists all files in the working \
directory on a distributed filesystem."

DFSImport::usage = "DFSImport[link, file] imports data from a file stored on \
distributed filesystem."

DFSExport::usage = "DFSExport[link, \"file.ext\", expr] exports data to a \
file on a distributed filesystem."

DFSAbsoluteFileName::usage = "DFSAbsoluteFileName[link, \"name\"] gives the \
full absolute version of the name for a file on the distributed filesystem."

DFSFileExistsQ::usage = "DFSFileExistsQ[link, \"name\"] gives True if the file \
with the specified name exists and gives False otherwise."

DFSFileQ::usage = "DFSFileQ[link, \"name\"] gives True if the file with the \
specified name exists, and gives False otherwise."

DFSDirectoryQ::usage = "DFSDirectoryQ[link, \"name\"] gives True if the \
directory with the specified name exists, and gives False otherwise."

DFSFileType::usage = "DFSFileType[link, \"name\"] gives the type of a file: \
File, Directory, or None."

DFSFileByteCount::usage = "DFSFileByteCount[link, \"name\"] gives the number \
of bytes in a file."

DFSFileDate::usage = "DFSFileDate[link, \"name\"] gives the date and time at \
which a file was last modified."

DFSDeleteFile::usage = "DFSDeleteFile[link, \"file\"] deletes a file."

DFSDeleteDirectory::usage = "DFSDeleteDirectory[link, \"dir\"] deletes the \
specified directory and all of its contents."

DFSRenameFile::usage = "DFSRenameFile[link, \"old\", \"name\"] renames the \
file \"old\" to \"new\"."

DFSRenameDirectory::usage = "DFSRenameDirectory[link \"old\", \"name\"] \
renames the directory \"old\" to \"new\"."

DFSCopyFile::usage = "DFSCopyFile[link, \"file1\",\"file2\"] copies file1 to file2."

DFSCopyDirectory::usage = "DFSCopyDirectory[link, \"dir1\",\"dir2\"] copies the \
directory dir1 to dir2."

DFSCopyFromLocal::usage = "DFSCopyFromLocal[link, \"local\", \"remote\"] \
copies a local file or directory to the distributed filesystem."

DFSCopyToLocal::usage = "DFSCopyToLocal[link, \"remote\", \"local\"] \
copies a file from the distributed filesystem to the local filesystem."

DFSCreateDirectory::usage = "DFSCreateDirectory[link, \"dir\"] creates a \
directory on the distributed filesystem."

HadoopMapReduceJob::usage = "HadoopMapReduceJob[link, \"jobname\", {\"input\"}, \"output\", \
mapfn, reducefn] submits a map-reduce job implemented in Mathematica."

Yield::usage = "Yield[key, value] writes a key-value pair to the ouput of a \
map or reduce task as part of a call to MapReduceJob."

IncrementCounter::usage = "IncrementCounter[\"name\", n] increments a named \
Hadoop counter by n when used as part of a call to MapReduceJob."

HBaseSetSchema::usage = "HBaseSetSchema[link, \"table\", key -> {\"Decoder\", ...} ...] \
sets the decoding scheme for an HBase table."

HBaseGet::usage = "HBaseGet[link, \"table\", \"key\"]"

HBaseCount::usage = "HBaseCount[link, \"table\"]"

HBaseListColumns::usage = "HBaseListColumns[link, \"table\"]"

HBaseScan::usage = "HBaseScan[link, \"table\"]"

HadoopLink::filex = "Cannot overwrite existing file '`1`' while performing `2`"
HadoopLink::nffil = "File '`1`' not found while performing `2`"
HadoopLink::nfglob = "No files matched `1` while performing `2`"
HadoopLink::ischema = "Schema key must be of the form \"key\", {<family>, <qualifier>} or {<family>, <qualifier>, <start>, <stop>}"

Begin["`Private`"]

$HadoopLinkPath = DirectoryName[$InputFileName];

Map[
	Import[FileNameJoin[{$HadoopLinkPath, #}]]&,
	{
		"DFS.m",
		"HadoopMapReduce.m",
		"HBase.m"
	}
];

(* Convenience function for throwing error messages. *)
die[message_String] := Throw[message, "HadoopLinkError"]

die[message_String, args__] := die[ToString@StringForm[message, args]]

property[HadoopLink[rls__Rule], name_String] :=
	name /. {rls} /. name -> Null

getObjectCache[h_, key_String] := 
    key /. property[h, "ObjectCache"] /. key -> Null

SetAttributes[deleteObjectCache, HoldFirst]
deleteObjectCache[h_, key_String] := Module[{oldJavaObjects},
    oldJavaObjects = Cases["ObjectCache" /. List @@ h, 
      (x_ -> y_JavaObject) /; x === key];
    h = h /. ("ObjectCache" -> _) -> ("ObjectCache" -> 
      DeleteCases["ObjectCache" /. List @@ h, (x_ -> y_) /; x === key]);
    ReleaseJavaObject /@ oldJavaObjects;
  ]

SetAttributes[clearObjectCache, HoldFirst]
clearObjectCache[h_] := Module[{oldJavaObjects},
    oldJavaObjects = Select["ObjectCache" /. List @@ h, JavaObjectQ[#[[2]]] &][[All, 2]];
    h = h /. ("ObjectCache" -> _) -> ("ObjectCache" -> {});
    ReleaseJavaObject /@ oldJavaObjects;
  ]

SetAttributes[putObjectCache, HoldFirst]
putObjectCache[h_, key_String, value_] := Module[{},
    deleteObjectCache[h, key];
    h = h /. ("ObjectCache" -> _) -> ("ObjectCache" ->
      Append["ObjectCache" /. List @@ h, key -> value]);
  ]

get[{rls___Rule}, key_String, default_:Null] := With[
  {match = Select[{rls}, #[[1]] === key &, 1]}, 
    If[match === {}, default, match[[1, 2]]]]

(* Set up a JVM and create a HadoopLink object to encapsulate
 * distributed file system access. *)
OpenHadoopLink[opts___Rule] :=
	Module[
		{hadoopLink, properties},
		properties = Cases[{opts}, HoldPattern[_String -> _String]];
		hadoopLink = HadoopLink["Configuration" -> properties, "ObjectCache" -> {}];
		initializeJLinkForHadoop[hadoopLink];
		hadoopLink
	]

$hadoopLinkInitializedProperty = "com.wolfram.hadooplink.initialized";

jLinkInitializedForHadoopQ[] :=
	Module[
		{initQ},
		InstallJava[];
		LoadJavaClass["java.lang.System", StaticsVisible -> True];
		initQ = System`getProperty[$hadoopLinkInitializedProperty];
		ValueQ[initQ]
	]

initializeJLinkForHadoop[h_HadoopLink] :=
	Module[
		{javaVersion, config, hadoopVersion, hadoopHome, hbaseHome},
		InstallJava[];
		LoadJavaClass["java.lang.System", StaticsVisible -> True];
		
		(* Check Java version *)
		javaVersion = System`getProperty["java.version"];
		If[ToExpression@StringTake[javaVersion, 3] < 1.6,
			die["HadoopLink` requires Java 6 or higher"]];
		
		(* Add Hadoop + HBase jar files to the path *)
		config = property[h, "Configuration"];
		
		(* Check if HADOOP_HOME and HBASE_HOME exist *)
		hadoopHome = get[config, "HadoopHome", Environment["HADOOP_HOME"]]; 
        hbaseHome = get[config, "HBaseHome", Environment["HBASE_HOME"]];
        hadoopVersion = get[config, "HadoopVersion", $Failed];
        
        AddToClassPath[Sequence @@ Which[
          hadoopVersion =!= $Failed,
          FileNames["*.jar", FileNameJoin[
            {$HadoopLinkPath, "Java", "dist", ToLowerCase[hadoopVersion]}]],
          
          hadoopHome =!= $Failed && hbaseHome =!= $Failed,
          Select[FileNames["*.jar", {
            hadoopHome,
            hbaseHome,
            FileNameJoin[{hadoopHome, "lib"}],
            FileNameJoin[{hbaseHome, "lib"}],
            FileNameJoin[{hadoopHome, "contrib", "streaming"}]
          }], StringMatchQ[
          Last@FileNameSplit[#], {"hadoop-*.jar", "hbase-*.jar", "zookeeper-*.jar"}] &],
          
          True,
          FileNames["*.jar", FileNameJoin[
            {$HadoopLinkPath, "Java", "dist", "cdh3u2"}]]]];
		
		(* Set up XML implementation for Hadoop *)
        System`setProperty[
			"javax.xml.parsers.DocumentBuilderFactory",
            "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl"];
		System`setProperty[
			"javax.xml.transform.TransformerFactory",
			"com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl"];
			
		(* Mark the JVM as initialized for HadoopLink` *)
		System`setProperty[$hadoopLinkInitializedProperty,"True"];
	]

getConf[h_HadoopLink] :=
	JavaBlock@Module[
		{conf},
		conf = JavaNew["org.apache.hadoop.conf.Configuration"];
		KeepJavaObject[conf];
		Map[conf@set[Sequence@@#] &, property[h, "Configuration"]];
		conf
	]

End[]

EndPackage[]

