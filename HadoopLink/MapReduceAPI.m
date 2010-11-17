

(* Wrapper function for incrementing Hadoop counters in map or reduce tasks *)
IncrementCounter[{group_String, name_String}, n_Integer] :=
	$task@incrementCounter[group, name, n]


IncrementCounter[name_String, n_Integer] :=
	$task@incrementCounter[name, n]

(* Wrapper function for writing output records from a map or reduce task *)
Yield[k_, v_] := $task@write[k, v]

(* Sets up context for an individual map or reduce call *)
MapReduceImplementation[task_, fn_Function, k_, v_] :=
	Block[{$task = task}, fn[k, v]]