(* Functions for map-reduce jobs *)

(* Local definitions of the map-reduce task API functions *)
Yield[k_, v_] := Print[ToString@k<>"\t"<>ToString@v]

IncrementCounter[___] = Null;

SetAttributes[fullDefString, HoldFirst];

fullDefString[sym_] := ToString@InputForm[FullDefinition[sym]]

(* Get the definition dependencies for a pure function as a string,
 * removing any definitions for symbols that will be redefined in the
 * map-reduce task kernels. *)
getFunctionDependencies[fn_Function] :=
	Module[
		{fnSym},
		fnSym = fn;
		StringReplace[
			fullDefString[fnSym],
			{
				ToString@InputForm[Definition[fnSym]] -> "",
				fullDefString[Yield] -> "",
				fullDefString[IncrementCounter] -> ""
			}
		]
	]


(* Unpack jar file into a temporary directory, add the listed files, and
 * repackage it as a new jar, whose path will be returned. *)
reJar[jarFile_String, files0 : {__String}] /; FileExistsQ[jarFile] :=
	Module[
		{files, tmp, newJarFile, exprs, types},
		files = Select[files0, FileExistsQ];
		tmp = CreateDirectory[];
		(* Extract the contents of the jar file to a temporary directory *)
		ExtractArchive[jarFile, tmp];
		(* Copy the files to add to the temporary directory *)
		Map[
			CopyFile[#, FileNameJoin[{tmp, Last@FileNameSplit[#]}]]&,
			files
		];
		{exprs, types} = Transpose@Cases[
			Select[FileNames[___, tmp, Infinity], !DirectoryQ[#]&],
			s_String :> {Import[s, "Binary"], {StringReplace[s,tmp<>"/" -> ""], "Binary"}}
		];
		(* Generate a timestamped name for the new jar file *)
		newJarFile = FileNameJoin[{
			$TemporaryDirectory,
			FileBaseName[jarFile]<>ToString@Floor@AbsoluteTime[]<>".jar"
		}];
		Export[newJarFile, exprs, {"ZIP", types}];
		DeleteDirectory[tmp, DeleteContents -> True];
		newJarFile
	]

MapReduceJob[h_HadoopLink,
			 name_String,
			 inputPaths : {__String},
			 outputPath_String,
			 mapper_Function,
			 reducer_Function
			 ] :=
	JavaBlock@Module[
		{conf, tmp, jar, job, jobRef},

		(* Ensure that Java and the Hadoop classes are properly initialized.
		 * Must call ReinstallJava here, so that only the repacked HadoopLink
		 * jar will be on the classpath. *)
		UninstallJava[];
		initializeJLinkForHadoop[h];
		Sow[JavaClassPath[]];

		conf = getConf[h];

		(* Test for Mathematica configuration on cluster machines in Hadoop
		 * conf. Technically, this only needs to be defined in the
		 * configuration on the tasktracker nodes.
		 *
		 * wolfram.jlink.path:
		 * Location of the JLink jar file in the cluster's Mathematica
		 * installation. Typically SystemFiles/Links/JLink in the Mathematica
		 * directory.
		 *
		 * wolfram.math.args:
		 * Arguments passed in the start up of a Mathematica kernel on the
		 * cluster.
		 *)
		Map[
			If[ conf@get[#] == Null,
				die["Please define `` in your Hadoop configuration", #]
			]&,
			{"wolfram.jlink.path", "wolfram.math.args"}
		];

		(* Find the definitions of any dependencies of the map and reduce
		 * functions, write them out to temporary files, and repackage the
		 * HadoopLink jar with the dependencies included. *)
		tmp = CreateDirectory[];
		Export[
			FileNameJoin[{tmp, "map.m"}],
			getFunctionDependencies[mapper],
			"Text"
		];
		Export[
			FileNameJoin[{tmp, "reduce.m"}],
			getFunctionDependencies[reducer],
			"Text"
		];
		jar = reJar[
			First@FileNames["HadoopLink-*.jar", FileNameJoin[{$HadoopLinkPath, "Java"}]],
			FileNameJoin[{tmp, #}]& /@ {"map.m", "reduce.m"}
		];

		AddToClassPath[jar];

		(* Initialize a new Mathematica map-reduce job *)
		job = JavaNew["com.wolfram.hadoop.mapreduce.MathematicaJob", name];

		(* Define input paths *)
		job@addInputPath[#]& /@ inputPaths;

		(* Define output path *)
		job@setOutputPath[outputPath];

		(* Define the map and reduce implementation functions *)
		job@setMapFunction[mapper];
		job@setReduceFunction[reducer];

		(* Launch the job asynchronously *)
		jobRef = job@launch[conf];
		KeepJavaObject[jobRef];
		JobInProgress[jobRef]
	]

SetAttributes[progressBar, HoldFirst];

progressBar[expr_] =
	Dynamic@Refresh[
		ProgressIndicator[
			expr,
			ImageSize -> {150, 15}
		],
		UpdateInterval -> 5
	]

JobInProgress /: Format[JobInProgress[jobRef_]] :=
	Module[
		{url, jobId, mapPercent, reducePercent, complete},
		url = jobRef@getTrackingURL[];
		jobId = StringCases[url, "jobid="~~id__ :> id][[1]];
		mapPercent = progressBar[jobRef@mapProgress[]];
		reducePercent = progressBar[jobRef@reduceProgress[]];

		Panel[
			Grid[
				{
					{Style["Job Details:", Bold], Hyperlink[jobId, url]},
					{Style["Map Progress:", Bold], mapPercent},
					{Style["Reduce Progress:", Bold], reducePercent}
				},
				Alignment -> Left
			]
		]
	]
