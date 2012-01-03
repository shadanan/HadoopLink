getHBaseAdmin[h_HadoopLink] := JavaBlock[
    JavaNew["org.apache.hadoop.hbase.client.HBaseAdmin", getConf[h]]]

toBytesBinary[str_String] := JavaBlock[
    LoadJavaClass["org.apache.hadoop.hbase.util.Bytes", StaticsVisible -> True];
    Bytes`toBytesBinary[str]]

getHBaseGet[str_String] := JavaBlock[
    JavaNew["org.apache.hadoop.hbase.client.Get", toBytesBinary[str]]]

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

appendHBaseHTablePackedBinaryDecoder[h_HadoopLink, tablestr_String, decode_String] :=
    Module[{table},
      table = getHBaseHTable[h, tablestr];
      table@setPackedBinaryDecoder[decode]]

appendHBaseHTablePackedBinaryDecoder[h_HadoopLink, tablestr_String, 
      family_String, qualifier_String, decode_String] :=
    Module[{table},
      table = getHBaseHTable[h, tablestr];
      table@setPackedBinaryDecoder[toBytesBinary[family], toBytesBinary[qualifier], decode]]

appendHBaseHTablePackedBinaryDecoder[h_HadoopLink, tablestr_String, 
      start_Integer, stop_Integer, family_String, qualifier_String, decode_String] :=
    Module[{table},
      table = getHBaseHTable[h, tablestr];
      table@setPackedBinaryDecoder[toBytesBinary[family], toBytesBinary[qualifier], start, stop, decode]]

SetAttributes[HBaseSetSchema, HoldFirst]
HBaseSetSchema[h_, tablestr_String, schema___Rule] := 
    Module[
      {table, index, rule, key, decoder},
      clearHBaseHTable[h, tablestr];
      table = getHBaseHTable[h, tablestr];
      
      For[index = 1, index <= Length[{schema}], index += 1,
        rule = {schema}[[index]];
        key = rule[[1]];
        
        decoder = JavaNew["com.wolfram.hbase." <> rule[[2, 1]], Sequence @@ rule[[2, 2 ;;]]];
        If[decoder === $Failed, Return[$Failed]];
        
        Which[
          MatchQ[key, _String] && key === "key",
          table@setDecoder[decoder],
        
          MatchQ[key, List[_String, _String]],
          table@setDecoder[toBytesBinary[key[[1]]], toBytesBinary[key[[2]]], decoder],
        
          MatchQ[key, List[_String, _String, _Integer, _Integer]],
          table@setDecoder[toBytesBinary[key[[1]]], toBytesBinary[key[[2]]], key[[3]], key[[4]], decoder],
          
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

HBaseListColumns[h_HadoopLink, table_String] := Module[{admin, tabledesc, columns},
    admin = getHBaseAdmin[h];
    tabledesc = admin@getTableDescriptor[toBytesBinary[table]];
    columns = tabledesc@getColumnFamilies[];
    #@getNameAsString[] & /@ columns]

HBaseGet[h_HadoopLink, tablestr_String, key_String] := 
    Module[{table, get},
      table = getHBaseHTable[h, tablestr];
      get = getHBaseGet[key];
      table@getDecoded[get]
    ]