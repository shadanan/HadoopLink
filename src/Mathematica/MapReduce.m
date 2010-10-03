(* Functions for map-reduce jobs *)

Protect[$$mapreduce];

Emit[k_, v_] := Sow[{k,v}, $$mapreduce]

MapReduceJob[h_HadoopLink,
			 name_String,
			 inputPaths : {__String},
			 outputPath_String,
			 mapper : _Symbol | _Function,
			 reducer : _Symbol | _Function] :=
	JavaBlock@Module[
		{job, conf, mapfn, reducefn},
		InstallJava[];
		If[ !jLinkInitializedForHadoopQ[], initializeJLinkForHadoop[h]];
		job = JavaNew["com.wolfram.hadoop.MathematicaJob", name];
		conf = getConf[h];
		(* Test for Mathematica configuration in Hadoop conf *)
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
		(* Define the map function for the job *)
		Switch[mapper,

			_Function,
			mapfn = mapper,

			_Symbol,
			mapfn = Function[{k,v}, mapper[k,v]];
		];
		job@setMapFunction[ ];
		(* Define the reduce function for the job *)
		Switch[reducer,
			(* *)
			_Function,
			reducefn = reducer,

			_Symbol,
			reducefn = Function[{k,v}, reducer[k,v]];
		];
		
		job@setReduceFunction[ ];
		(* Launch the job asynchronously *)
		job@launch[]; 
	]