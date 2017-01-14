import java.io.IOException;

public class Main {


    public static void main(String[] args) {
        //если протокол HTTP
        //аргументы:
        //1. тип соединения
        //2. URL
        //3. файл для сохранения
        if (args[0].equals("http"))
            HTTPPoller.start(args);

        //если протокол POP3
        //аргументы:
        //1. тип соединения
        //2. сервер
        //3. логин
        //4. пароль
        if (args[0].equals("pop3")) {
            try {
                POP3Poller mailPoller = new POP3Poller(args[1], args[2], args[3]);
                mailPoller.writeNewMessageInFile("newMessage.txt");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AuthorizationException e) {
                System.out.println(e.getMessage());
            } catch (Exception e) {}
        }
    }
}
