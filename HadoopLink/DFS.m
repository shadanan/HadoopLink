(* Functions for interacting with distributed file systems. *)

getDefaultDFS[h_HadoopLink] :=
	JavaBlock@Module[{fs, conf},
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
		AddToClassPath[FileNameJoin[{$HadoopLinkPath, "Java"}]];
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

DFSGlobStatus[h_HadoopLink, pattern_String] :=
    dfsModule[h, {}, $DFS@globStatus[JavaNew[$path, pattern]]]

(* DFSFileNames: operates like FileNames, but on the distributed file system
 * referenced by HadoopLink. *)
DFSFileNames[h_HadoopLink] :=
	DFSFileNames[h, "*"]

DFSFileNames[h_HadoopLink, form_] :=
	DFSFileNames[h, form, {}]

DFSFileNames[h_HadoopLink, forms_, dir_String] :=
	DFSFileNames[h, forms, {dir}]

(* Return a directory listing for the provided exact path, returning a list of
 * length 0 when the path does not exist.
 *
 * Must be called from within a dfsModule! *)
listingForPathname[pathname_String] /; StringFreeQ[pathname, "*"] :=
	Module[
		{path},
		path = JavaNew[$path, pathname];
		If[ $DFS@exists[path],
			$DFS@listStatus[path],
			{}
		]
	]

(* Return a directory listing for the provided glob path.
 *
 * Must be called from within a dfsModule! *)
listingForPathname[pathname0_String] :=
	Module[
		{pathname, path},
		(* Add a glob for files in the matched directories if not present *)
		pathname = pathname0 /. s_?(StringTake[#, -2] != "/*"&) :> s<>"/*";
		path = JavaNew[$path, pathname];
		$DFS@globStatus[path]
	]

DFSFileNames[h_HadoopLink, forms_, dirs0 : {___String}] :=
	dfsModule[h,
		{dirs, names},

		(* List user's home directory if no directories are provided *)
		If[ Length@dirs0 == 0,
			dirs = {$DFS@getHomeDirectory[]@toUri[]@getPath[]},
			dirs = dirs0
		];

		names = Flatten@Map[listingForPathname, dirs];

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
        If[!DFSFileExistsQ[h, file],
            Message[HadoopLink::nffil, file, "DFSImport"];
            Return[$Failed]];
            
		recordsPerFetch = 10000;
		path = JavaNew[$path, file];
		Check[
			reader = JavaNew["com.wolfram.hadoop.dfs.SequenceFileImportReader", $Configuration, path];
			results = {};
			While[(chunk = reader@next[recordsPerFetch]) =!= Null,
				AppendTo[results, chunk];
			];
			reader@close[];
			Flatten[results, 1],
			die["Error reading from " <> file];
		]
	]

DFSImport[h_HadoopLink, file_String, args___] :=
	dfsModule[h, {tempDir, filename},
	    If[!DFSFileExistsQ[h, file],
	        Message[HadoopLink::nffil, file, "DFSImport"];
            Return[$Failed]];
            
		(* Generate a temporary working directory *)
		tempDir = CreateDirectory[];
		(* Download the file from DFS to the local tamp*)
		Check[
			$DFS@copyToLocalFile[JavaNew[$path, file], JavaNew[$path, tempDir]],
            DeleteDirectory[tempDir, DeleteContents -> True];
            die["Could not write to local file"]
		];
		filename = Last@FileNameSplit[file, OperatingSystem -> "Unix"];
		results = Import[FileNameJoin[{tempDir, filename}], args];
		(* Clean up *)
		DeleteDirectory[tempDir, DeleteContents -> True];
		results
	]

DFSExport[h_HadoopLink, file_String, expr_, "SequenceFile"] :=
	dfsModule[h, {recordsPerWrite, writer, path},
	    If[DFSFileExistsQ[h, file],
	        Message[HadoopLink::filex, file, "DFSExport"];
	        Return[$Failed]];
	  
		recordsPerWrite = 10000;
		(* Check that expr has dimensions appropriate to a list of key-value pairs. *)
		If[ !MatchQ[Dimensions[expr], {_,2,___}],
			die["Can only export lists of pairs."]
		];
		path = JavaNew[$path, file];
		Check[
			writer = JavaNew["com.wolfram.hadoop.dfs.SequenceFileExportWriter", $Configuration, path];
			writer@write[expr];
			writer@close[];
			file,
			die["Could not write sequence file to DFS"]
		]
	]

DFSExport[h_HadoopLink, file_String, args___] :=
	dfsModule[h,
		{tempDir, filename, tempFile},
		
        If[DFSFileExistsQ[h, file],
            Message[HadoopLink::filex, file, "DFSExport"];
            Return[$Failed]];

		(* Export the file locally *)
		tempDir = CreateDirectory[];
		filename = Last@FileNameSplit[file, OperatingSystem -> "Unix"];
		tempFile = FileNameJoin[{tempDir, filename}];
		Check[
		    Export[tempFile, args], 
		    DeleteDirectory[tempDir, DeleteContents -> True];
		    Return[$Failed]];

		(* Copy the exported file to HDFS *)
		Check[
			$DFS@copyFromLocalFile[JavaNew[$path, tempFile], JavaNew[$path, file]],
			die["Could not write file to DFS"]
		];

		(* Clean up *)
		DeleteDirectory[tempDir, DeleteContents -> True];
		file
	]

DFSAbsoluteFileName[h_HadoopLink, file_String] :=
	dfsModule[h,
		{path, status},
        If[!DFSFileExistsQ[h, file],
            Message[HadoopLink::nffil, file, "DFSAbsoluteFileName"];
            Return[$Failed]];
            
		path = JavaNew[$path, file];
		status = $DFS@getFileStatus[path];
        status@getPath[]@toUri[]@getPath[]
	]

DFSFileExistsQ[h_HadoopLink, file_String] :=
	dfsModule[h, {},
		$DFS@exists[JavaNew[$path, file]]
	]

DFSDirectoryQ[h_HadoopLink, file_String] :=
	dfsModule[h, {status, path},
		path = JavaNew[$path, file];
		If[ $DFS@exists[path],
			status = $DFS@getFileStatus[path];
			status@isDir[],
			False
		]
	]

DFSFileQ[h_HadoopLink, file_String] :=
    DFSFileType[h, file] === File;

DFSFileType[h_HadoopLink, file_String] :=
	dfsModule[h, {status, path},
		path = JavaNew[$path, file];
		Which[
			!$DFS@exists[path],
			None,

			status = $DFS@getFileStatus[path];
			status@isDir[],
			Directory,

			True,
			File
		]
	]

DFSFileByteCount[h_HadoopLink, file_String] :=
	dfsModule[h,
		{status, path},
        If[!DFSFileExistsQ[h, file],
            Message[HadoopLink::nffil, file, "DFSFileByteCount"];
            Return[$Failed]];
            
		path = JavaNew[$path, file];
        status = $DFS@getFileStatus[path];
        status@getLen[]
	]

(* Start of epoch, in local AbsoluteTime *)
$epoch = AbsoluteTime[DatePlus[{1970, 1, 1}, {$TimeZone, "Hour"}]];

DFSFileDate[h_HadoopLink, file_String] :=
	dfsModule[h,
		{status, path, t},
        If[!DFSFileExistsQ[h, file],
            Message[HadoopLink::nffil, file, "DFSFileDate"];
            Return[$Failed]];
            
		path = JavaNew[$path, file];
		status = $DFS@getFileStatus[path];
		t = status@getModificationTime[];
		DateList[N[t/1000] + $epoch]
	]

DFSDeleteFile[h_HadoopLink, file_String] :=
	DFSDeleteFile[h, {file}]

DFSDeleteFile[h_HadoopLink, files : {__String}] :=
	dfsModule[h, {},
        If[Plus @@ (Length[DFSGlobStatus[h, #]] & /@ files) == 0,
            Message[HadoopLink::nfglob, files, "DFSDeleteFile"];
            Return[$Failed]];

	    Check[
			Map[$DFS@delete[JavaNew[$path, #], False] &, files];,
			$Failed
	    ]
	]

DFSDeleteDirectory[h_HadoopLink, directory_String] :=
	dfsModule[h, {},
	    If[Length[DFSGlobStatus[h, directory]] == 0,
            Message[HadoopLink::nfglob, directory, "DFSDeleteDirectory"];
            Return[$Failed]];
            
		Quiet@Check[
			$DFS@delete[JavaNew[$path, directory], True];,
			$Failed
		]
	]

DFSRenameFile[h_HadoopLink, old_String, new_String] :=
	dfsModule[h, {oldPath, newPath},
        If[!DFSFileExistsQ[h, old],
            Message[HadoopLink::nffil, old, "DFSRenameFile"];
            Return[$Failed]];
            
        If[DFSFileExistsQ[h, new],
            Message[HadoopLink::filex, new, "DFSRenameFile"];
            Return[$Failed]];
            
		oldPath = JavaNew[$path, old];
		newPath = JavaNew[$path, new];
		Quiet@Check[
			$DFS@rename[oldPath, newPath];,
			$Failed
		]
	]

DFSRenameDirectory = DFSRenameFile;

DFSCopyFile[h_HadoopLink, file1_String, file2_String] :=
	dfsModule[h, {path1, path2},
        If[!DFSFileExistsQ[h, file1],
            Message[HadoopLink::nffil, file1, "DFSCopyFile"];
            Return[$Failed]];
            
        If[DFSFileQ[h, file2],
            Message[HadoopLink::filex, file2, "DFSCopyFile"];
            Return[$Failed]];
            
		path1 = JavaNew[$path, file1];
		path2 = JavaNew[$path, file2];
		LoadJavaClass["org.apache.hadoop.fs.FileUtil", StaticsVisible -> True];
		Quiet@Check[
			FileUtil`copy[
				path1@getFileSystem[$Configuration],
				path1,
				path2@getFileSystem[$Configuration],
				path2,
				False,
				$Configuration
			];,
			$Failed
		]
	]

DFSCopyDirectory = DFSCopyFile;
DFSCopyFromLocal[h_HadoopLink, localName0_String, dfsName_String] :=
	dfsModule[h, {localName, path1, path2},
		localName = StringReplace[localName0, "~" -> $HomeDirectory];
		
		If[!FileExistsQ[localName],
		    Message[HadoopLink::nffil, localName, "DFSCopyFromLocal"];
		    Return[$Failed]];
		
        If[DFSFileQ[h, dfsName],
            Message[HadoopLink::filex, dfsName, "DFSCopyFromLocal"];
            Return[$Failed]];
		
		path1 = JavaNew[$path, localName];
		path2 = JavaNew[$path, dfsName];
		Quiet@Check[
			$DFS@copyFromLocalFile[path1, path2];,
			$Failed
		]
	]

DFSCopyToLocal[h_HadoopLink, dfsName_String, localName0_String] :=
	dfsModule[h, {localName, path1, path2},
		localName = StringReplace[localName0, "~" -> $HomeDirectory];
		
		If[!DFSFileExistsQ[h, dfsName],
		    Message[HadoopLink::nffil, dfsName, "DFSCopyToLocal"];
		    Return[$Failed]];
		
		If[FileExistsQ[localName],
		    Message[HadoopLink::filex, localName, "DFSCopyToLocal"];
		    Return[$Failed]];
		
		path1 = JavaNew[$path, dfsName];
		path2 = JavaNew[$path, localName];
		If[ !$DFS@exists[path1],
			Return[$Failed]
		];
		Quiet@Check[
			$DFS@copyToLocalFile[path1, path2];,
			$Failed
		]
	]

DFSCreateDirectory[h_HadoopLink, dir_String] :=
	dfsModule[h, {path},
        If[DFSFileExistsQ[h, dir],
            Message[HadoopLink::filex, dir, "DFSCreateDirectory"];
            Return[$Failed]];
            
		path = JavaNew[$path, dir];
		Quiet@Check[
			$DFS@mkdirs[path];,
			$Failed
		]
	]
