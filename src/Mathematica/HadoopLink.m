BeginPackage["HadoopLink`", {"JLink`"}]

HadoopLink::usage = "HadoopLink[args] is an object that represents a "<>
"connection to a Hadoop cluster, via a locally installed Hadoop distribution."

OpenHadoopLink::usage = "OpenHadoopLink[hadoopHome] sets up Mathematica "<>
"for interaction with a Hadoop cluster, via the local Hadoop distribution "<>
"found at hadoopHome." 

Begin["`Private`"]

(* Convenience function for throwing error messages. *)
die[message_String] := Throw[message, "HadoopLinkError"]

HadoopLink /: Format[HadoopLink[rls__Rule]] :=
	HadoopLink["HadoopHome"/.{rls}]

(* Set up a JVM and create a HadoopLink object to encapsulate
 * distributed file system access.
 *)
OpenHadoopLink[hadoopHome_String] :=
	Module[
		{hadoopLink},
		hadoopLink = HadoopLink["HadoopHome"->hadoopHome];
		initializeJLinkForHadoop[hadoopLink];
		hadoopLink
	]

$hadoopLinkInitializedProperty = "com.wolfram.hadooplink.initialized";

jLinkInitializedForHadoopQ[] :=
	Module[
		{initQ},
		InstallJava[];
		LoadJavaClass["java.lang.System", StaticsVisible -> True];
		initQ = System`getProperty[$hadoopLinkInitializedProperty];
		ValueQ[initQ]
	]

initializeJLinkForHadoop[h : HadoopLink[rls__]] :=
	Module[
		{javaVersion, hadoopHome},
		InstallJava[];
		LoadJavaClass["java.lang.System", StaticsVisible -> True];
		(* Check Java version *)
		javaVersion = System`getProperty["java.version"];
		If[ ToExpression@StringTake[javaVersion, 3] < 1.6,
			die["HadoopLink` requires Java 6 or higher"];
		];
		(* Set up XML implementation for Hadoop *)
        System`setProperty[
			"javax.xml.parsers.DocumentBuilderFactory",
            "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl"
		];
		(* Add all the Hadoop jars to the classpath *)
		hadoopHome = "HadoopHome" /. {rls};
		AddToClassPath[Flatten@Map[
			FileNames["*.jar", #]&,
			{
				hadoopHome,
			 	FileNameJoin[{hadoopHome,"lib"}],
			 	FileNameJoin[{hadoopHome, "contrib", "streaming"}]
			}
		]];
		(* TODO: Add xalan to the classpath *)
		(* Mark the JVM as initialized for HadoopLink` *)
		System`setProperty[
			$hadoopLinkInitializedProperty,
			"True"
		];
	]

End[]

EndPackage[]
