package omendezv.Producer;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Scanner;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TwitterProducer {

    Logger logger = LoggerFactory.getLogger(TwitterProducer.class.getName());

    List<String> terms = Lists.newArrayList("bitcoin", "usa", "politics", "sport", "soccer");


    public TwitterProducer(){}

    public static void main(String[] args) {

        String consumerKey = "";
        String consumerSecret = "";
        String token = "";
        String secret = "";

        /** We ask for credentials to the twitter API **/
        Scanner input = new Scanner(System.in);
        System.out.println("Enter the consumer API Key: ");
        consumerKey = input.nextLine();
        System.out.println("Enter the consumer API secret: ");
        consumerSecret = input.nextLine();
        System.out.println("Enter the access token: ");
        token = input.nextLine();
        System.out.println("Enter the access secret: ");
        secret = input.nextLine();

        new TwitterProducer().run(consumerKey, consumerSecret, token, secret);
    }

    public void run(String consumerKey, String consumerSecret, String token, String secret){

        logger.info("Setup");

        /** Setting up blocking queues. In this case, we set the value to 1000 as we should never reach this number */
        BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(1000);

        // create a twitter client passing all the info
        Client client = createTwitterClient(msgQueue, consumerKey, consumerSecret, token, secret);
        // Attempts to establish a connection.
        client.connect();

        // create a kafka producer
        KafkaProducer<String, String> producer = createKafkaProducer();

        // add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("stopping application...");
            logger.info("shutting down client from twitter...");
            client.stop();
            logger.info("closing producer...");
            producer.close();
            logger.info("done!");
        }));

        // loop to send tweets to kafka
        // on a different thread, or multiple different threads....
        while (!client.isDone()) {
            String msg = null;
            try {
                msg = msgQueue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                client.stop();
            }
            if (msg != null){
                logger.info(msg);
                producer.send(new ProducerRecord<>("twitter_tweets", null, msg), new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                        if (e != null) {
                            logger.error("Something bad happened", e);
                        }
                    }
                });
            }
        }
        logger.info("End of application");
    }

    public Client createTwitterClient(BlockingQueue<String> msgQueue, String consumerKey, String consumerSecret, String token, String secret){

        /** Declare the host you want to connect to, the endpoint, and authentication (basic auth or oauth) */
        Hosts twitterHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint twitterEndpoint = new StatusesFilterEndpoint();

        twitterEndpoint.trackTerms(terms);

        // These secrets should be read from a config file
        Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, token, secret);

        ClientBuilder builder = new ClientBuilder()
                .name("Twitter-Client-01")
                .hosts(twitterHosts)
                .authentication(hosebirdAuth)
                .endpoint(twitterEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue));

        Client twitterClient = builder.build();
        return twitterClient;
    }

    public KafkaProducer<String, String> createKafkaProducer(){
        String bootstrapServers = "127.0.0.1:9092";

        // create Producer properties
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // create safe Producer
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        // high throughput producer (at the expense of a bit of latency and CPU usage)
        properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
        properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32*1024));

        // create the producer
        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);
        return producer;
    }
}
