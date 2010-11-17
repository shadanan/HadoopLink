(* This package file is only used on remote kernels, as part of the context of
 * Mathematica map-reduce tasks. *)


(* Wrapper function for incrementing Hadoop counters in map or reduce tasks *)
IncrementCounter[{group_String, name_String}, n_Integer] :=
	$task@incrementCounter[group, name, n]


IncrementCounter[name_String, n_Integer] :=
	$task@incrementCounter[name, n]

(* Wrapper function for writing output records from a map or reduce task *)
Yield[key_, value_] := $task@write[key, value]

(* Evaluate an individual map call *)
MapImplementation[task_, mapper_Function, key_, value_] :=
	Block[
		{$task = task},

		mapper[key, value];
	]

(* Evaluate an individual reduce call *)
ReduceImplementation[task_, reducer_Function, key_, values_] :=
	Block[
		{$task = task},

		(* Define List[] for the iterator so the user can instantiate its
		   contents as a list, if memory overhead is not a concern. *)
		List[values] ^:= NestWhileList[
			values@next[]&,
			Unevaluated[Sequence[]],
			values@hasNext[]&
		];

		reducer[key, values];
	]
