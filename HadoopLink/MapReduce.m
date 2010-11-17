(* Functions for map-reduce jobs *)

Protect[$$mapreduce];

MapReduceJob[h_HadoopLink,
			 name_String,
			 inputPaths : {__String},
			 outputPath_String,
			 mapper_Function,
			 reducer_Function
			 ] :=
	JavaBlock@Module[
		{job, conf},

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
		job@launch[]; 
	]
