package mypackage;

import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InetAddressWithTimeout {
    public static void main(String[] args) {    // for testing purpose
        final String[] ipsToGet = {
            "51.38.24.96",
            "51.38.24.97",
            "51.38.24.98",
            "51.38.24.99",
            "51.38.24.100",
            "51.38.24.101",
            "51.38.24.102",
            "51.38.24.103",
            "51.38.24.104",
        };

        ExecutorService executor = Executors.newFixedThreadPool(ipsToGet.length);

        for(String ip : ipsToGet){
            InetAddressLookup lookup = new InetAddressLookup( ip );
            Future<String> future = executor.submit(lookup);

            try {
                String hostName = future.get(4, TimeUnit.SECONDS); // Set your timeout here
                System.out.println("IP Address: " + ip + ", Host Name: " + hostName);
            } catch (TimeoutException e) {
                System.out.println("IP Address: " + ip + ", Timed out");
            } catch (Exception e) {
                System.err.println("IP Address: " + ip + ", An error occurred: " + e.getMessage());
            }
        }

        executor.shutdown();
    }

    public static String getHostName(String ipString){
        ExecutorService executor = Executors.newFixedThreadPool(1);
        InetAddressLookup lookup = new InetAddressLookup( ipString );
        Future<String> future = executor.submit(lookup);
        String hostName = ipString;
        try {
            hostName = future.get(3, TimeUnit.SECONDS); // Set your timeout here
            //System.out.println("IP Address: " + ipString + ", Host Name: " + hostName);
        } catch (TimeoutException e) {
            System.out.println("Timeout for IP Address: " + ipString);
            hostName = ipString;
        } catch (Exception e) {
            System.err.println("IP Address: " + ipString + ", An error occurred: " + e.getMessage());
        }
        executor.shutdown();
        return hostName;
    }
}

class InetAddressLookup implements Callable<String> {
    private String address;

    public InetAddressLookup(String address){
        
        this.address = address;

    }

    @Override
    public String call() throws Exception {
        InetAddress addr = InetAddress.getByName(address);
        return addr.getHostName();
    }
}