import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.EnumSet;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class HTTPPoller {
    private static final int BUF_SIZE = 1024;
    public static int TIMEOUT = 5000;

    public static void start(String[] args) {
        if (args.length < 3) {
            System.out.println("Need more arguments!");
            return;
        }

        try {
            //установили первичное соединение
            URL url = new URL(args[1]);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("GET");
            conn.connect();

            int responseCode = conn.getResponseCode();
            long time = conn.getLastModified()  ;
            System.out.println("Response code: " + responseCode);
            System.out.println("Last Modified: " + new Date(time));


            //раз в 5 секунд проверяем наличие обновлений
            //если появилось обновление, то обновляем файл
            while (true) {
                try {
                    HttpURLConnection fileCheck = (HttpURLConnection) url.openConnection();
                    fileCheck.setRequestMethod("GET");
                    fileCheck.setIfModifiedSince(time);
                    fileCheck.connect();
                    responseCode = fileCheck.getResponseCode();

                    if (responseCode != conn.HTTP_NOT_MODIFIED) {
                        System.out.println("File was changed! Updating...");
                        updateFile(args[2], fileCheck.getInputStream());
                        return;
                    }

                    System.out.println("Response code: " + responseCode);
                    Thread.sleep(TIMEOUT);
                } catch (InterruptedException e) {}
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //метод, в котором осуществляется обновление файла
    private static void updateFile(String fileName, InputStream newDataStream) throws IOException {
        Path path = Paths.get(fileName);
        SeekableByteChannel fileWriter = Files.newByteChannel(path, EnumSet.of(WRITE, CREATE));
        writeDataToFile(newDataStream, fileWriter);
        fileWriter.close();
    }

    //метод, который записывает данные в файл
    private static void writeDataToFile(InputStream httpBodyStream, SeekableByteChannel fileWriter) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
        byte[] tempArray = new byte[BUF_SIZE];
        int result = httpBodyStream.read(tempArray);

        while (result != -1) {
            buf.clear();
            buf.put(tempArray, 0, result);
            buf.flip();
            fileWriter.write(buf);
            result = httpBodyStream.read(tempArray);
        }
    }
}
