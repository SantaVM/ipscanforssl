package mypackage;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class IPRangeCalculator {

    public static List<InetAddress> getInetAddresses(String ipAddressWithMask){
        List<InetAddress> ipRange = new ArrayList<>();
        
        try {
            String[] parts = ipAddressWithMask.split("/");
            if (parts.length != 2) {
                System.out.println("Неправильный формат IP-адреса с маской.");
                throw new UnknownHostException("Неправильный формат IP-адреса с маской.");
            }

            String ipAddress = parts[0];
            int maskLength = Integer.parseInt(parts[1]);

            InetAddress inetAddress = Inet4Address.getByName(ipAddress);
            byte[] ipBytes = inetAddress.getAddress();

            if (maskLength < 0 || maskLength > 32) {
                System.out.println("Неправильная длина маски.");
                throw new UnknownHostException("Неправильная длина маски.");
            }

            int subnetMask = 0xFFFFFFFF << (32 - maskLength);
            byte[] maskBytes = new byte[] {
                (byte) (subnetMask >>> 24),
                (byte) (subnetMask >> 16 & 0xFF),
                (byte) (subnetMask >> 8 & 0xFF),
                (byte) (subnetMask & 0xFF)
            };

            byte[] startIPBytes = new byte[4];
            byte[] endIPBytes = new byte[4];

            for (int i = 0; i < 4; i++) {
                startIPBytes[i] = (byte) (ipBytes[i] & maskBytes[i]);
                endIPBytes[i] = (byte) (ipBytes[i] | ~maskBytes[i]);
            }

            InetAddress startIP = Inet4Address.getByAddress(startIPBytes);
            InetAddress endIP = Inet4Address.getByAddress(endIPBytes);

            System.out.println("IP-range:");
            System.out.println("Start IP: " + startIP.getHostAddress() + " name: " + InetAddressWithTimeout.getHostName(startIP.getHostAddress()));
            System.out.println("End IP: " + endIP.getHostAddress() + " name: " + InetAddressWithTimeout.getHostName(endIP.getHostAddress()));

            //generateAllIPAddressesInRange(startIP, endIP);
            
            ipRange = generateAllIPAddressesInRange(startIP, endIP);

        } catch (UnknownHostException e) {
            System.out.println("Неверный IP-адрес.");
            e.printStackTrace();
        }
        return ipRange;
    }

    private static List<InetAddress> generateAllIPAddressesInRange(InetAddress startAddress, InetAddress endAddress) {
        List<InetAddress> ipRange = new ArrayList<>();
        
        InetAddress currentAddress = startAddress;
        ipRange.add(currentAddress);

        while (!currentAddress.equals(endAddress)) {
            //System.out.println("Next address: " + currentAddress.getHostAddress());

            currentAddress = getNextIPAddress(currentAddress);
            ipRange.add(currentAddress);
        }

        return ipRange;
    }

    private static InetAddress getNextIPAddress(InetAddress currentAddress) {
        byte[] addr = currentAddress.getAddress();

        for (int i = 3; i >= 0; i--) {
            if (++addr[i] != 0) {
                break;
            }
        }

        try {
            return Inet4Address.getByAddress(addr);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    // 
    public static List<String> getURIList(List<InetAddress> ipRange){
        List<String> arrayStrings = new ArrayList<>();
        //String[] arrayStrings = new String[ ipRange.size() ];  // more, then needed
        int i = 0;
        
        for( InetAddress ip : ipRange ){
            
            String ipString = ip.getHostAddress();
            String ipHostString = InetAddressWithTimeout.getHostName(ipString);
            if( !ipString.equals(ipHostString) ){ // не добавлять в массив если имя == адресу ip.getHostName()
                String url = "https://" + ipHostString;
                if( !arrayStrings.contains( url ) ){  // проверяем на дубликаты
                    arrayStrings.add( url );
                    System.out.println(i + " Address: " + arrayStrings.get(i));
                    i++;
                }
            }
        }

        return arrayStrings;
    }
}
