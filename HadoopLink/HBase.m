getHBaseAdmin[h_HadoopLink] := JavaBlock[
    JavaNew["org.apache.hadoop.hbase.client.HBaseAdmin", getConf[h]]]

toBytesBinary[str_String] := JavaBlock[
    LoadJavaClass["org.apache.hadoop.hbase.util.Bytes", StaticsVisible -> True];
    Bytes`toBytesBinary[str]]

encodeHTableKey[table_, key_String] := key;

encodeHTableKey[table_, key_List] := Module[{array, i},
    LoadJavaClass["org.apache.hadoop.hbase.util.Bytes", StaticsVisible -> True];
    LoadJavaClass["java.lang.reflect.Array", StaticsVisible -> True];
    array = ReturnAsJavaObject[Array`newInstance[JavaNew["java.lang.Object"]@getClass[], Length[key]]];
    For[i = 0, i < Length[key], i += 1,
      Array`set[array, i, MakeJavaObject[key[[i + 1]]]]];
    Bytes`toStringBinary[table@getTranscoder[]@encode[array]]]

SetAttributes[getHBaseHTable, HoldFirst]
getHBaseHTable[h_, tablestr_String] := 
    If[getObjectCache[h, tablestr] === Null,
      With[{table = JavaNew["com.wolfram.hbase.HTable", getConf[h], toBytesBinary[tablestr]]},
        putObjectCache[h, tablestr, table]; 
        table],
      getObjectCache[h, tablestr]]

SetAttributes[clearHBaseHTable, HoldFirst]
clearHBaseHTable[h_, tablestr_String] :=
    deleteObjectCache[h, tablestr]

SetAttributes[HBaseSetSchema, HoldFirst]
HBaseSetSchema[h_, tablestr_String, schema___Rule] := 
    Module[
      {table, index, rule, key, transcoder},
      clearHBaseHTable[h, tablestr];
      table = getHBaseHTable[h, tablestr];
      
      For[index = 1, index <= Length[{schema}], index += 1,
        rule = {schema}[[index]];
        key = rule[[1]];
        
        transcoder = JavaNew["com.wolfram.hbase." <> rule[[2, 1]], Sequence @@ rule[[2, 2 ;;]]];
        If[transcoder === $Failed, Return[$Failed]];
        
        Which[
          MatchQ[key, _String] && key === "key",
          table@setTranscoder[transcoder],
        
          MatchQ[key, List[_String, _String]],
          table@setTranscoder[toBytesBinary[key[[1]]], toBytesBinary[key[[2]]], transcoder],
        
          MatchQ[key, List[_String, _String, _Integer, _Integer]],
          table@setTranscoder[toBytesBinary[key[[1]]], toBytesBinary[key[[2]]], key[[3]], key[[4]], transcoder],
          
          True,
          Message[HadoopLink::ischema];
          Return[$Failed];
        ]
      ]
    ]

HBaseListTables[h_HadoopLink] := Module[{admin, tables},
    admin = getHBaseAdmin[h];
    tables = admin@listTables[];
    #@getNameAsString[] & /@ tables]

HBaseDescribeTable[h_HadoopLink, tablestr_String] := Module[{admin, table},
    admin = getHBaseAdmin[h];
    table = admin@getTableDescriptor[toBytesBinary[tablestr]];
    {
      "name" -> table@getNameAsString[],
      "families" -> ({
        "name" -> #@getNameAsString[], 
        "bloomFilter" -> #@getBloomFilterType[]@toString[],
        "replicationScope" -> #@getScope[],
        "compression" -> #@getCompression[]@toString[],
        "versions" -> #@getMaxVersions[],
        "timeToLive" -> #@getTimeToLive[],
        "blocksize" -> #@getBlocksize[],
        "inMemory" -> #@isInMemory[],
        "blockCache" -> #@isBlockCacheEnabled[]} & /@ table@getColumnFamilies[])
    }]

HBaseListColumns[h_HadoopLink, tablestr_String] := Module[{admin, tabledesc, columns},
    admin = getHBaseAdmin[h];
    tabledesc = admin@getTableDescriptor[toBytesBinary[tablestr]];
    columns = tabledesc@getColumnFamilies[];
    #@getNameAsString[] & /@ columns]

Options[HBaseGet] = {
  "Columns" -> None,
  "Families" -> None,
  "Column" -> None,
  "Family" -> None,
  "Versions" -> 1,
  "TimeStamp" -> None,
  "TimeRange" -> None
}

HBaseGet[h_HadoopLink, tablestr_String, key_, opts : OptionsPattern[HBaseGet]] := 
  Module[{table, get},
    table = getHBaseHTable[h, tablestr];
    
    get = JavaNew["org.apache.hadoop.hbase.client.Get", toBytesBinary[encodeHTableKey[table, key]]];
    If[OptionValue["Families"] =!= None,
      get@addFamily[toBytesBinary[#]] & /@ OptionValue["Families"]];
    If[OptionValue["Columns"] =!= None,
      get@addColumn[toBytesBinary[#[[1]]], toBytesBinary[#[[2]]]] & /@ OptionValue["Columns"]];
    If[OptionValue["Family"] =!= None,
      get@addFamily[toBytesBinary[OptionValue["Family"]]]];
    If[OptionValue["Column"] =!= None,
      get@addColumn[toBytesBinary[OptionValue["Column"][[1]]], toBytesBinary[OptionValue["Column"][[2]]]]];
    
    get@setMaxVersions[OptionValue["Versions"]];
    If[OptionValue["TimeStamp"] =!= None,
      get@setTimeStamp[OptionValue["TimeStamp"]]];
    If[OptionValue["TimeRange"] =!= None,
      get@setTimeRange[OptionValue["TimeRange"][[1]], OptionValue["TimeRange"][[2]]]];
      
    table@getDecoded[get]]

Options[HBaseCount] = {
  "CachingRows" -> 1000
}

HBaseCount[h_HadoopLink, tablestr_String, opts : OptionsPattern[HBaseCount]] := 
  Module[{table},
    table = getHBaseHTable[h, tablestr];
    Monitor[table@countDecoded[OptionValue["CachingRows"]], Refresh[StringJoin[{
        "Count: ", ToString[table@getCurrentCount[]], "  Row: ", ToString[table@getCurrentRow[]]}], 
      UpdateInterval -> 0.5]]]

Options[HBaseScan] = {
  "StartRow" -> None,
  "StopRow" -> None,
  "Columns" -> None,
  "Families" -> None,
  "Column" -> None,
  "Family" -> None,
  "Versions" -> 1,
  "TimeStamp" -> None,
  "TimeRange" -> None,
  "Filter" -> None,
  "CacheBlocks" -> True,
  "Limit" -> All
}

HBaseScan[h_HadoopLink, tablestr_String, opts : OptionsPattern[HBaseScan]] :=
  Module[{table, scan, limit},
    table = getHBaseHTable[h, tablestr];
    
    scan = JavaNew["org.apache.hadoop.hbase.client.Scan"];
    If[OptionValue["StartRow"] =!= None,
      scan@setStartRow[toBytesBinary[encodeHTableKey[table, OptionValue["StartRow"]]]]];
    If[OptionValue["StopRow"] =!= None,
      scan@setStopRow[toBytesBinary[encodeHTableKey[table, OptionValue["StopRow"]]]]];
    
    If[OptionValue["Families"] =!= None,
      scan@addFamily[toBytesBinary[#]] & /@ OptionValue["Families"]];
    If[OptionValue["Columns"] =!= None,
      scan@addColumn[toBytesBinary[#[[1]]], toBytesBinary[#[[2]]]] & /@ OptionValue["Columns"]];
    If[OptionValue["Family"] =!= None,
      scan@addFamily[toBytesBinary[OptionValue["Family"]]]];
    If[OptionValue["Column"] =!= None,
      scan@addColumn[toBytesBinary[OptionValue["Column"][[1]]], toBytesBinary[OptionValue["Column"][[2]]]]];
    If[And @@ (OptionValue[#] === None & /@ {"Columns", "Families", "Column", "Family"}),
      scan@addFamily[toBytesBinary[#]] & /@ HBaseListColumns[h, tablestr]];

    If[OptionValue["Filter"] =!= None,
      scan@setFilter[OptionValue["Filter"]]];
    scan@setCacheBlocks[OptionValue["CacheBlocks"]];
    scan@setMaxVersions[OptionValue["Versions"]];
    If[OptionValue["TimeStamp"] =!= None,
      scan@setTimeStamp[OptionValue["TimeStamp"]]];
    If[OptionValue["TimeRange"] =!= None,
      scan@setTimeRange[OptionValue["TimeRange"][[1]], OptionValue["TimeRange"][[2]]]];
    
    table@setScan[scan];
    limit = If[OptionValue["Limit"] === All, -1, OptionValue["Limit"]];
    Monitor[table@scanDecoded[limit], Refresh[StringJoin[{
      "Count: ", ToString[table@getCurrentCount[]], "  Row: ", ToString[table@getCurrentRow[]]}], 
      UpdateInterval -> 0.5]]]