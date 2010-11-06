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

(* DFSFileNames: operates like FileNames, but on the distributed file system
 * referenced by HadoopLink. *)
DFSFileNames[h_HadoopLink] :=
	DFSFileNames[h, "*"]

DFSFileNames[h_HadoopLink, form_] :=
	DFSFileNames[h, form, {"/user/"<>$UserName}]

DFSFileNames[h_HadoopLink, forms_, dir_String] :=
	DFSFileNames[h, forms, {dir}]

DFSFileNames[h_HadoopLink, forms_, dirs : {__String}] :=
	JavaBlock@Module[
		{$path, conf, fs, uri, trimUrl, listMatchingNames},
		(* Double-check the JVM state *)
		InstallJava[];
		If[ !jLinkInitializedForHadoopQ[], initializeJLinkForHadoop[h]];
		LoadJavaClass["org.apache.hadoop.fs.FileSystem", StaticsVisible -> True];
		$path = LoadJavaClass["org.apache.hadoop.fs.Path"];
		fs = getDefaultDFS[h];
		(* Set up a function to trim the DFS URL off of filenames *)
		conf = getConf[h];
		uri = FileSystem`getDefaultUri[conf]@toString[];
		trimUrl[s_String] := StringDrop[s, StringLength@uri - 5];
		(* Return the filenames matching the supplied form in one directory *)
		listMatchingNames[dir_String] := Select[
			Map[
				trimUrl[#@getPath[]@toString[]]&,
				fs@listStatus[JavaNew[$path, dir]]
			],
			StringMatchQ[
				FileNameSplit[#, OperatingSystem->"Unix"][[-1]],
				forms
			]&
		];
		Flatten[listMatchingNames /@ Select[dirs, fs@exists[JavaNew[$path, #]]&]]
	]

DFSImport[h_HadoopLink, file_, "SequenceFile"] :=
	JavaBlock@Module[
		{recordsPerFetch, reader, conf, path, results, chunk},
		recordsPerFetch = 10000;
		InstallJava[];
		If[ !jLinkInitializedForHadoopQ[], initializeJLinkForHadoop[h]];
		conf = getConf[h];
		path = JavaNew["org.apache.hadoop.fs.Path", file];
		Check[
			reader = JavaNew["com.wolfram.hadoop.SequenceFileImportReader", conf, path];
			results = {};
			While[(chunk = reader@next[recordsPerFetch]) =!= Null,
				AppendTo[results, chunk];
			];
			reader@close[];
			Flatten[results, 1],
			die["Error reading from "<>file];
		]
	]

(* Import functionality from the DFS *)
DFSImport[h_HadoopLink, file_, args___] :=
	JavaBlock@Module[
		{fs, tempfile, $path, results},
		(* Double-check the JVM state *)
		InstallJava[];
		If[ !jLinkInitializedForHadoopQ[], initializeJLinkForHadoop[h]];
		fs = getDefaultDFS[h];
		tempfile = Close[OpenWrite[]];
		DeleteFile[tempfile];
		$path = LoadJavaClass["org.apache.hadoop.fs.Path"];
		Check[
			fs@copyToLocalFile[JavaNew[$path, file], JavaNew[$path, tempfile]],
			die["Could not write to local file"]
		];
		results = Import[tempfile, args];
		DeleteFile[tempfile];
		results
	]

(* Export to the DFS *)
DFSExport[h_HadoopLink, file_, args___] :=
	JavaBlock@Module[
		{fs, dir, dfsPath, filename, tempfile, $path, results},
		(* Double-check the JVM state *)
		InstallJava[];
		If[ !jLinkInitializedForHadoopQ[], initializeJLinkForHadoop[h]];
		(* Export the file locally *)
		dir = CreateDirectory[];
		With[
			{parts=FileNameSplit[file]},
			dfsPath = Most[parts];
			filename = Last[parts];
		];
		tempfile = FileNameJoin[{dir, filename}];
		Export[tempfile, args];
		(* Copy the exported file to HDFS *)
		fs = getDefaultDFS[h];
		$path = LoadJavaClass["org.apache.hadoop.fs.Path"];
		Check[
			fs@copyFromLocalFile[JavaNew[$path, tempfile], JavaNew[$path, file]],
			die["Could not write file to DFS"]
		];
		(* Clean up *)
		DeleteDirectory[dir, DeleteContents -> True];
		file
	]
