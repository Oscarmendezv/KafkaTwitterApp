package omendezv.Consumer;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

public class ElasticSearchConsumer {

    public static RestHighLevelClient createClient(){

        //////////////////////////
        /////////// Because I use ElasticSearch locally, I use this builder for the RestClient.
        //////////////////////////
        String hostname = "localhost";
        RestClientBuilder builder = RestClient.builder(new HttpHost(hostname,22,"http"));
        RestHighLevelClient client = new RestHighLevelClient(builder);

        return client;
    }
}
