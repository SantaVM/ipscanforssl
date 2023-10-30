package mypackage;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class Server {
    public static void main(String[] args) throws Exception {

        Javalin app = Javalin.create(
            config -> {
                //config.staticFiles.add("./public");   //from Javalin 5.6.3
                config.addStaticFiles(sf -> {   //from Javalin 4.6.8
                    sf.hostedPath = "/";
                    sf.directory = "./public";
                    sf.location = Location.EXTERNAL;
                });
            }).start(7000);

        app.get("/file", ctx -> {
            // Получаем путь к текстовому файлу в папке /public
            String filePath = "./public/domains.txt";

            // Читаем содержимое файла
            String fileContent = "";
            try {
                fileContent = new String(Files.readAllBytes(Paths.get(filePath)));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Устанавливаем контент тип как plain text
            ctx.contentType("text/plain");
            // Отправляем содержимое файла в ответ
            ctx.result(fileContent);
        });

        app.post("/scan", ctx -> {

            // Parse the user input to get IP range and number of threads
            String ipRange = ctx.formParam("ipRange");
            int numThreads = Integer.parseInt(ctx.formParam("numThreads"));
            String sendFile = ctx.formParam("sendFile");

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
                String html = "<!DOCTYPE html><html lang=\"ru\"><meta charset=\"UTF-8\"><body>";
                html += "<h3>You can get file named \"domains.txt\" in folder \"public\"</h3>";
                html += "<a href=\"/file\">Ссылка на текстовый файл</a>";
                html += "</body></html>";
    
                // Устанавливаем контент тип как HTML
                ctx.contentType("text/html");
                ctx.result(html);
            }
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
    
}
