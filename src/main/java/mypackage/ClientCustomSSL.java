package mypackage;

import java.net.InetAddress;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.InMemoryDnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;

public class ClientCustomSSL {
    
    public static StringBuffer performGet( Map<String, InetAddress> args, int numThreads ) throws Exception {
        StringBuffer resultSB = new StringBuffer();

        // Trust standard CA and those trusted by our custom strategy
        final SSLContext sslContext = SSLContexts.custom()
                // Custom TrustStrategy implementations are intended for verification
                // of certificates whose CA is not trusted by the system, and where specifying
                // a custom truststore containing the certificate chain is not an option.
                .loadTrustMaterial(new TrustStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] chain, String authType) {
                        // Поверяем сертификат без проверки доверия
                        return true;
                    }})
                .build();
        final SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .setHostnameVerifier(new NoopHostnameVerifier()) // test
                .build();
        // try to manage timeouts
        final ConnectionConfig cc = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setSocketTimeout(Timeout.ofSeconds(5))
                .build();

        final InMemoryDnsResolver dr = new InMemoryDnsResolver();
            for(Map.Entry<String, InetAddress> entry : args.entrySet()){
                dr.add( entry.getKey().substring(8), entry.getValue() );
            }

        // Allow every version of TLS protocol
        final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .setDnsResolver(dr)
                .setDefaultConnectionConfig(cc) //try to manage timeouts
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setHandshakeTimeout(Timeout.ofSeconds(5))
                        //.setSupportedProtocols(TLS.V_1_1, TLS.V_1_2, TLS.V_1_3)
                        .build())
                .setMaxConnTotal( numThreads ) // number of working threads
                .setMaxConnPerRoute(1)
                .build();
       
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build()) {

            // -------- Threads start

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            // create a thread for each URI
            List<GetThread> threads = new ArrayList<>();
            List<String> uriList = new ArrayList<>( args.keySet() );

            for (int i = 0; i < args.size(); i++) {
                HttpGet httpget = new HttpGet( uriList.get(i) );
                HttpClientContext clientContext = HttpClientContext.create();
                threads.add( new GetThread(httpclient, clientContext, httpget, i + 1, resultSB) );
            }
            
            //Execute all tasks and get reference to Future objects
            List< Future<X509Certificate> > futureList = null;
            try {
                futureList = executor.invokeAll(threads);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            executor.shutdown();

            for (int i = 0; i < futureList.size(); i++) {
                Future<X509Certificate> future = futureList.get(i);
                try {
                    X509Certificate cert  = future.get(6, TimeUnit.SECONDS);
                    if(cert == null) continue;
                    Collection<List<?>> subjectAlternativeNames = cert.getSubjectAlternativeNames();
                    if (subjectAlternativeNames != null) {
                        for (List<?> san : subjectAlternativeNames) {
                            Integer nameType = (Integer) san.get(0);
                            if (nameType == 2) { // 2 соответствует DNS-имени
                                String domainName = (String) san.get(1);
                                resultSB.append("Domain: " + domainName + "\n");
                            }
                        }
                    }
                } catch (TimeoutException e) {
                    System.out.println("Thread: " + i + ", Timed out");                
                } catch (InterruptedException | ExecutionException e) {
                  e.printStackTrace();
                }
            }

            // -------- Threads end
        } catch (Exception e){
            e.printStackTrace();
        }


        return resultSB;
    }

    /**
     * A thread that performs a GET.
     */
    static class GetThread implements Callable<X509Certificate> {

        private final CloseableHttpClient httpClient;
        private final HttpClientContext context;
        private final HttpGet httpget;
        public final int id;
        private StringBuffer sb;

        private X509Certificate sslCertificate;

        public GetThread(final CloseableHttpClient httpClient, final HttpClientContext context, final HttpGet httpget, final int id, StringBuffer sb) {
            this.httpClient = httpClient;
            this.context = context;
            this.httpget = httpget;
            this.id = id;
            this.sb = sb;
        }

        /**
         * Executes the GetMethod and prints some status information.
         */
        @Override
        public X509Certificate call() throws Exception {
            
            try {
                String reqURI = httpget.getUri().toString();
                System.out.println(id + " - about to get something from " + reqURI);
                //sb.append(id + " - " +reqURI + "\n");
                this.httpClient.execute(httpget, context, response -> {
                    System.out.println(id + " - get executed. Status is -> " +  new StatusLine(response));
                    // get the response body as an array of bytes
                    EntityUtils.consume(response.getEntity());
                    final SSLSession sslSession = context.getSSLSession();
                    if (sslSession != null) {
                        sb.append(id + " - " +reqURI+" Principal name: " + sslSession.getPeerPrincipal().getName() + "\n");
                        this.sslCertificate = (X509Certificate)sslSession.getPeerCertificates()[0];
                        return sslCertificate;                   
                    }

                    return null;
                });
            } catch (final Exception e) {
                System.out.println(id + " - error: " + e);
            }
            return sslCertificate;
        }
    }
}
