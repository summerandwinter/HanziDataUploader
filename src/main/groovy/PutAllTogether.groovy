import com.mongodb.BasicDBObject
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoCursor
import db.GroovyDataLoader
import groovy.sql.Sql
import org.bson.Document

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Created by shaopengxiang on 2016/11/18.
 */
class PutAllTogether {

    MongoCollection<Document> tsjsCollection;
    MongoCollection<Document> poemCollection
    MongoCollection<Document> shangxiCollection

    def cols = ['_id', 'mingcheng', 'zuozhe', 'shipin', 'ticai', 'chaodai', 'guojia', 'fenlei', 'jieduan', 'keben', 'congshu', 'chuchu', 'zhaiyao', 'yuanwen']


    def dbLoader =GroovyDataLoader.instance


    def perform() {

        dbLoader.copyDbs()

        def mongoDb = dbLoader.getOnlineDb()
        poemCollection = mongoDb.getCollection("poem");
        tsjsCollection = mongoDb.getCollection("tsjs");
        shangxiCollection = mongoDb.getCollection("shangxi");


        def sql_poem = Sql.newInstance("jdbc:sqlite:poem.db", "", "", "org.sqlite.JDBC")

        def sql_shangxi = Sql.newInstance("jdbc:sqlite:poem_shangxi.db", "", "", "org.sqlite.JDBC")
        def sql_yiwen = Sql.newInstance("jdbc:sqlite:poem_yiwen.db", "", "", "org.sqlite.JDBC")
        def sql_zhujie = Sql.newInstance("jdbc:sqlite:poem_zhujie.db", "", "", "org.sqlite.JDBC")
//        def sql_zuozhe = Sql.newInstance("jdbc:sqlite:poem_zuozhe.db", "", "", "org.sqlite.JDBC")

        def sqlmap = ["shangxi": sql_shangxi, "yiwen": sql_yiwen, "zhujie": sql_zhujie]

        def count = 0
        BasicDBObject query = new BasicDBObject();
        def sql_id_list = "select * from poem where _id >=0 "

        sql_poem.eachRow(sql_id_list) {
            row ->
                def pid = row["_id"]
                def pname = row["mingcheng"]
                def pauthor = row["zuozhe"]
                def yuanwen = row['yuanwen']
                query.put("_id", pid);
                def find = poemCollection.find(query)

                if (find.size() == 0) {
                    Document shangxiDocument = new Document();
                    Document document = new Document();
                    document.append("pid", "" + row["_id"]);


                    cols.each { col ->
                        if (col != '_id')
                            document.append("$col", "" + row[col]);
                    }

                    sqlmap.each { tablename, sql ->

                        sql.eachRow("select " + tablename + " from " + tablename + " where _id=" + pid){ queryRs ->
                            byte[] bytes = queryRs[tablename]
//                            println "byte array length is :"+ bytes.length
//                            println tablename+" is :"+ bytes
                            if (bytes) {
                                int m = (bytes.length / 16);
//                                println "m:"+m
                                int i = m*16;
                                if (i >= 16) {
                                    String result = doSomething(bytes, i);
                                    document.append(tablename, "" + result);
                                }
                            } else {
                                document.append(tablename, "");
                            }
                        }

                    }


                    String shangxi = document.get("shangxi");
                    ArrayList<Document> shagnxiList = new ArrayList<>();
//                    BasicDBList shagnxiList = new BasicDBList();
                    if(shangxi.length()>0){
                        Document shangxidata = new Document();
                        shangxidata.put("shangxi", shangxi.trim())
                        shangxidata.put("src", " gscd")
                        shangxidata.put("srcDesc", "古诗词典")
                        shagnxiList.add(shangxidata)

                    }

                    BasicDBObject tsjsQuery = new BasicDBObject();
                    tsjsQuery.put("n", pname);
                    tsjsQuery.put("a", pauthor);

//                    BasicDBObject regexQuery = new BasicDBObject();
//                    regexQuery.put("n",
//                            new BasicDBObject("$regex", "TestEmployee_[3]")
//                                    .append("$options", "i"));

//                    BasicDBObject fields = new BasicDBObject();
//                    fields.put("sx", 1);

                    def tsjsfind = tsjsCollection.find(tsjsQuery)
                    String tsjsshangxi = ""
                    //println "tsjsfind.getName() :"+tsjsfind.getClass().getName()
                    if (tsjsfind.size() != 0) {
                        MongoCursor<Document> cursor = tsjsfind.iterator();
                        try {
                            while (cursor.hasNext()) {
                                Document doc = cursor.next();

                                tsjsshangxi +=doc.get("sx");
                            }
                        } finally {
                            cursor.close();
                        }

                        if(tsjsshangxi.length()>0){
                            Document shangxidata = new Document();
                            shangxidata.put("shangxi", tsjsshangxi.trim())
                            shangxidata.put("src", "tsjs")
                            shangxidata.put("srcDesc", "唐诗鉴赏词典")
                            shagnxiList.add(shangxidata)
                        }

                        //println tsjsshangxi
                    }
                    //document.append("shangxis", shagnxiList);

                    shagnxiList.forEach(){ doc->
                        doc.append("pid", "" + row["_id"]);
                        doc.append("name", "" + pname);
                        doc.append("author", "" + pauthor);
                        doc.append("yuanwen", "" + yuanwen);
                        shangxiCollection.insertOne(doc)
                    }

                    poemCollection.insertOne(document);
                } else {
                    //poemCollection.up
                }

                println '' + pid
        }

        dbLoader.clearDbs()
    }


    public static byte[] b(byte[] paramArrayOfByte, int paramInt) {
//        println "paramArrayOfByte.length:"+paramArrayOfByte.length
//        println "paramInt:"+paramInt
        try {
            Cipher localCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            localCipher.init(2, new SecretKeySpec("13f439726d2d4522".getBytes(), "AES"));
            byte[] arrayOfByte = localCipher.doFinal(paramArrayOfByte, 0, paramInt);
            return arrayOfByte;
        } catch (Exception localException) {
            localException.printStackTrace();
        }
        return null;
    }

    private static String doSomething(byte[] arrayOfByte, int i) {
        return new String(b(arrayOfByte, i));
    }


    static def main(args) {
        new PutAllTogether().perform()
    }
}
