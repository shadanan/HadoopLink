getHBaseAdmin[h_HadoopLink] := JavaBlock[
    JavaNew["org.apache.hadoop.hbase.client.HBaseAdmin", getConf[h]]]

toBytesBinary[str_String] := JavaBlock[
    LoadJavaClass["org.apache.hadoop.hbase.util.Bytes", StaticsVisible -> True];
    Bytes`toBytesBinary[str]]

getHBaseGet[str_String] := JavaBlock[
    JavaNew["org.apache.hadoop.hbase.client.Get", toBytesBinary[str]]]

getHBaseHTable[table_String] := JavaBlock[
    JavaNew["org.apache.hadoop.hbase.client.HTable", 
      getConf[h], toBytesBinary[table]]]

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

HBaseGet[h_HadoopLink, table_String, row_String] := Module[{htable, get},
    htable = getHBaseHTable[table];
    get = getHBaseGet[row];
    htable@get[get];
]