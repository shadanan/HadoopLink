(* Functions for map-reduce jobs *)

(* Local definitions of the map-reduce task API functions *)
Yield[k_, v_] := Print[ToString@k<>"\t"<>ToString@v]

IncrementCounter[___] = Null;

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

MapReduceJob[h_HadoopLink,
			 name_String,
			 inputPaths : {__String},
			 outputPath_String,
			 mapper_Function,
			 reducer_Function
			 ] :=
	JavaBlock@Module[
		{conf, job, jobRef},

		(* Ensure that Java and the Hadoop classes are properly initialized *)
		InstallJava[];
		If[ !jLinkInitializedForHadoopQ[], initializeJLinkForHadoop[h]];

		conf = getConf[h];

		(* Initialize a new Mathematica map-reduce job *)
		job = JavaNew["com.wolfram.hadoop.MathematicaJob", name];

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
