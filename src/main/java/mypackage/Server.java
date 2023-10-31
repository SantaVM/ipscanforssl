package mypackage;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.javalin.Javalin;

public class Server {
    public static void main(String[] args) throws Exception {

        Javalin app = Javalin.create().start(7000);

        app.get("/", ctx -> {
            
            String qString = ctx.queryString();
            System.out.println(qString);
            if( qString == null ){ 
                ctx.redirect("static");
            } else {                    
                // Parse the user input to get IP range and number of threads
                String ipRange = ctx.queryParam("ipRange");
                int numThreads = Integer.parseInt(ctx.queryParam("numThreads"));
                String sendFile = ctx.queryParam("sendFile"); // "on"

                // Split the IP range into individual IP addresses
                String[] ipAddresses = ipRange.split("/");
                String startIP = ipAddresses[0].trim();
                String mask = ipAddresses[1].trim();

                List<InetAddress> ipRangeList = IPRangeCalculator.getInetAddresses(ipRange);

                StringBuffer resultSBuffer = new StringBuffer();

                Map<String, InetAddress> ipRangeMap = IPRangeCalculator.getURIMap(ipRangeList);
                try{
                    resultSBuffer = ClientCustomSSL.performGet( ipRangeMap, numThreads );
                    System.out.println(resultSBuffer.toString());
                }catch(Exception e){
                    e.printStackTrace();
                }

                if(resultSBuffer.length() == 0){
                    resultSBuffer.append("No Domains found!\n");
                }
                resultSBuffer.append("Start ip: " + startIP + ", mask: " + mask + ", Num threads: " + numThreads + " send file: " + sendFile);
                System.out.println("Start ip: " + startIP + ", mask: " + mask + ", Num threads: " + numThreads + " send file: " + sendFile);

                if(sendFile != null){
                    //  заголовок Content-Disposition, чтобы браузер отобразил диалоговое окно сохранения файла
                    ctx.header("Content-Disposition", "attachment; filename=scan_result.txt");
                    
                    //  MIME-тип файла (в данном случае, текстовый файл)
                    ctx.contentType("text/plain");

                    // Откройте файл и отправьте его в ответ
                    ctx.result(resultSBuffer.toString());
                    
                } else {
                    saveDomainsToFile(resultSBuffer.toString());
                    StringBuilder html = new StringBuilder();
                    html.append("<!DOCTYPE html><html lang=\"ru\"><meta charset=\"UTF-8\">");
                    html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
                    html.append("<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\">");
                    html.append("<body><div class=\"container\">");
                    html.append("<h2>You can get file named <span style=\"color: blue;\">\"domains.txt\"</span> in folder <span style=\"color: blue;\">\"public\"</span></h2>");
                    html.append("<a href=\"/file\">Ссылка на текстовый файл</a></div>");
                    html.append("<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.min.js\"></script>");
                    html.append("</body></html>");
        
                    ctx.html(html.toString());
                }
            }               
        });

        app.get("/static", ctx -> {
            String index = fileToString("./public/index.html");
            ctx.html(index);
        });

        app.get("/file", ctx -> {
            
            String fileContent = fileToString("./public/domains.txt");

            // Устанавливаем контент тип как plain text
            ctx.contentType("text/plain");
            // Отправляем содержимое файла в ответ
            ctx.result(fileContent); 
        });
    }
    
    private static void saveDomainsToFile(String domain) {
        try (FileWriter writer = new FileWriter("./public/domains.txt", false)) {  // from java 17: new FileWriter("./public/domains.txt", StandardCharsets.UTF_8, false))
            writer.write(domain);
        } catch (IOException e) {
            // Handle file write errors
            e.printStackTrace();
        }
    }
    
    private static String fileToString( String path ) {
        String filePath = path;
        String content = "";

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            StringBuilder contentSB = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                contentSB.append(line).append("\n");
            }
            content = contentSB.toString();
        } catch (IOException e) {
            System.err.println("Произошла ошибка при чтении файла: " + e.getMessage());
        }

        return content;
    }
   
}
