(* ::Package:: *)

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

		(* Create an Export[..., "ZIP"] spec corresponding to the directory
			structure we have built *)
		{exprs, types} = Transpose@Cases[
			Select[FileNames[___, tmp, Infinity], !DirectoryQ[#]&],
			s_String :> {Import[s, "Binary"], {StringReplace[
				StringReplace[s,tmp<>$PathnameSeparator -> ""],"\\"->"/"], "Binary"}}
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

HadoopMapReduceJob[h_HadoopLink, name_String, inputPath_String, args__] :=
	HadoopMapReduceJob[h, name, {inputPath}, args]

HadoopMapReduceJob[h_HadoopLink,
			 name_String,
			 inputPaths : {__String},
			 outputPath_String,
			 mapper_Function,
			 reducer_Function
			 ] :=
	JavaBlock@Module[
		{conf, jar, mapperPkg, reducerPkg, $job, job, jobRef},

		(* Ensure that Java and the Hadoop classes are properly initialized.
		 * UninstallJava must be called here to ensure that previous job jars
		 * are no longer on the classpath. *)
		UninstallJava[];
		initializeJLinkForHadoop[h];

		conf = getConf[h];

		(* Find the definitions of any dependencies of the map and reduce
		 * functions, write them out to temporary files, and repackage the
		 * HadoopLink jar with the dependencies included. *)
		mapperPkg = Close[OpenWrite[]];
		Export[
			mapperPkg,
			getFunctionDependencies[mapper],
			"Text"
		];
		reducerPkg = Close[OpenWrite[]];
		Export[
			reducerPkg,
			getFunctionDependencies[reducer],
			"Text"
		];

		(* repackage with new files *)
		jar = reJar[
			(* we use Last@FileNames to hopefully get the most recent version
				(in case several are present... we could be smarter. *)
			Last@FileNames["HadoopLink-mapreduce-*.jar", FileNameJoin[{$HadoopLinkPath, "Data"}]],
			{mapperPkg, reducerPkg}
		];
		AddToClassPath[jar];

		(* Initialize a new Mathematica map-reduce job *)
		$job = LoadJavaClass["com.wolfram.hadoop.mapreduce.MathematicaJob", StaticsVisible -> True];
		job = JavaNew[$job, name];

		(* Set the names of the map and reduce dependency packages *)
		conf@set[MathematicaJob`MAPPERUDEPENDENCIES, FileBaseName[mapperPkg]];
		conf@set[MathematicaJob`REDUCERUDEPENDENCIES, FileBaseName[reducerPkg]];

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

		(* Clean up temporary files *)
		DeleteFile[mapperPkg];
		DeleteFile[reducerPkg];
		Quiet[DeleteFile[jar]]; (* seems this temp file deletion can fail for permissions,
			at least on Windows. no biggie. *)

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
		{url, jobId, mapPercent, reducePercent},
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

