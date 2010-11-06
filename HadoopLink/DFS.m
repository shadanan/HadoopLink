(* Functions for interacting with distributed file systems. *)

getDefaultDFS[h_HadoopLink] :=
	JavaBlock@Module[
		{fs, conf},
		LoadJavaClass["org.apache.hadoop.fs.FileSystem", StaticsVisible -> True];
		conf = getConf[h];
		fs = FileSystem`get[conf];
		KeepJavaObject[fs];
		fs
	]

(* Module wrapper for DFS interaction functions. Initializes these variables:
 *
 *  $DFS - java object representing distributed filesystem
 *  $path - java class for org.apache.hadoop.fs.Path
 *)

SetAttributes[dfsModule, HoldAll];

dfsModule[h_HadoopLink, args_, expr_] :=
	JavaBlock@Block[
		{
			$Configuration,
			$DFS,
			$path
		},
		(* Make sure the JVM state is initialized for use with this link *)
		InstallJava[];
		If[ !jLinkInitializedForHadoopQ[], initializeJLinkForHadoop[h]];
		LoadJavaClass["org.apache.hadoop.fs.FileSystem", StaticsVisible -> True];
		$Configuration = getConf[h];
		$DFS = FileSystem`get[$Configuration];
		$path = LoadJavaClass["org.apache.hadoop.fs.Path"];
		(* Execute user code *)
		Module[args, expr]
	]

(* Prevent symbols provided by dfsModule from being overwritten *)
Protect[$Configuration];
Protect[$DFS];
Protect[$path];


(* DFSFileNames: operates like FileNames, but on the distributed file system
 * referenced by HadoopLink. *)
DFSFileNames[h_HadoopLink] :=
	DFSFileNames[h, "*"]

DFSFileNames[h_HadoopLink, form_] :=
	DFSFileNames[h, form, {}]

DFSFileNames[h_HadoopLink, forms_, dir_String] :=
	DFSFileNames[h, forms, {dir}]

DFSFileNames[h_HadoopLink, forms_, dirs0 : {___String}] :=
	dfsModule[h,
		{dirs, names},

		(* List user's home directory if no directories are provided *)
		If[ Length@dirs0 == 0,
			dirs = {$DFS@getHomeDirectory[]@toUri[]@getPath[]},
			dirs = dirs0
		];

		names = Flatten@Map[
			With[
				{p = JavaNew[$path, #]},
				If[ $DFS@exists[p],
					$DFS@listStatus[p],
					{}
				]
			]&,
			dirs
		];

		(* Show just the path component of the file URIs *)
		names = Map[#@getPath[]@toUri[]@getPath[]&, names];

		(* Find all returned paths matching supplied patterns *)
		Select[
			names,
			StringMatchQ[
				Last@FileNameSplit[#, OperatingSystem->"Unix"],
				forms
			]&
		]
	]

DFSImport[h_HadoopLink, file_String, "SequenceFile"] :=
	dfsModule[h,
		{recordsPerFetch, reader, path, results, chunk},
		recordsPerFetch = 10000;
		path = JavaNew[$path, file];
		Check[
			reader = JavaNew["com.wolfram.hadoop.SequenceFileImportReader", $Configuration, path];
			results = {};
			While[(chunk = reader@next[recordsPerFetch]) =!= Null,
				AppendTo[results, chunk];
			];
			reader@close[];
			Flatten[results, 1],
			die["Error reading from "<>file];
		]
	]

DFSImport[h_HadoopLink, file_String, args___] :=
	dfsModule[h,
		{tempDir, filename},
		(* Generate a temporary working directory *)
		tempDir = CreateDirectory[];
		(* Download the file from DFS to the local tamp*)
		Check[
			$DFS@copyToLocalFile[JavaNew[$path, file], JavaNew[$path, tempDir]],
			die["Could not write to local file"]
		];
		filename = Last@FileNameSplit[file, OperatingSystem -> "Unix"];
		results = Import[FileNameJoin[{tempDir, filename}], args];
		(* Clean up *)
		DeleteDirectory[tempDir, DeleteContents -> True];
		results
	]

DFSExport[h_HadoopLink, file_String, args___] :=
	dfsModule[h,
		{tempDir, filename, tempFile},

		(* Export the file locally *)
		tempDir = CreateDirectory[];
		filename = Last@FileNameSplit[file, OperatingSystem -> "Unix"];
		tempFile = FileNameJoin[{tempDir, filename}];
		Export[tempFile, args];

		(* Copy the exported file to HDFS *)
		Check[
			$DFS@copyFromLocalFile[JavaNew[$path, tempFile], JavaNew[$path, file]],
			die["Could not write file to DFS"]
		];

		(* Clean up *)
		DeleteDirectory[tempDir, DeleteContents -> True];
		file
	]
