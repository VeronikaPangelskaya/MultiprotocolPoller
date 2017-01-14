import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class POP3Poller {
    private final String newLineDelimiter = "\r\n";
    private final String errorMessage = "-ERR";
    private final String successMessage = "+OK";
    public static int TIMEOUT = 5000;
    //сервер
    private String mailServer = "";
    //логин
    private String userName;
    //пароль
    private String userPass;
    private boolean authorized = false;
    private Socket socket;
    private OutputStreamWriter command;
    private BufferedReader answer;
    private ArrayList<Mail> mailList = new ArrayList<>();
    private int messageCounter = 0;

    class Mail {
        private int size;
        private int number;
        private String content;

        public Mail(int _number, int _size) {
            number = _number;
            size = _size;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public POP3Poller(String _mailServer, String _userName, String _userPass)
                    throws IOException, AuthorizationException {
        mailServer = _mailServer;
        userName = _userName;
        userPass = _userPass;

        try {
            connect();
            getMailList();
        } catch (AuthorizationException e) {
            socket.close();
            throw e;
        } catch (Exception e) {
            socket.close();
        }
    }

    //метод, в котором устанавливается соединение с сервером и запускается авторизацию
    private void connect() throws Exception {
        try {
            socket = new Socket(mailServer, 110);
            command = new OutputStreamWriter(socket.getOutputStream());
            answer = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            auth();
            authorized = true;
        } catch (UnknownHostException e) {
            throw new Exception("Invalid mail server!");
        } catch (IOException e) {
            throw e;
        }
    }

    //метод, в котором получаем количество сообщений
    private void getMailStat() throws IOException {
        ArrayList<String> tempCollection = new ArrayList<>();
        answerParser("STAT", tempCollection, false);
        StringTokenizer tokenizer = new StringTokenizer(tempCollection.get(0), " ");
        messageCounter = Integer.parseInt(tokenizer.nextToken());
    }

    //метод, который получает сообщение и возвращает его
    private String getMessageBody(int messageNumber) throws IOException {
        ArrayList<String> message = new ArrayList<>();
        answerParser("RETR " + messageNumber, message, true);
        String messageBody = "";

        Iterator<String> iter = message.iterator();
        while (iter.hasNext()) {
            String newLine = iter.next();
            messageBody += newLine;
        }

        return messageBody;
    }

    //получение списка ссобщений
    private void getMailList() throws IOException {
        //получаем список сообщений
        ArrayList<String> tempCollection = new ArrayList<>();
        answerParser("LIST", tempCollection, true);
        Iterator<String> iter = tempCollection.iterator();

        //преобразуем список сообщений в список нашенго типа
        while (iter.hasNext()) {
            String currentMail = iter.next();
            StringTokenizer tokenizer = new StringTokenizer(currentMail, " ");
            mailList.add(new Mail(Integer.parseInt(tokenizer.nextToken()), Integer.parseInt(tokenizer.nextToken())));
        }

        //количество полученных сообщений
        messageCounter = mailList.size();
    }

    //парсер ответов
    private void answerParser(String commandName, ArrayList<String> collection, boolean havePoint) throws IOException {
        command.write(commandName + newLineDelimiter);
        command.flush();

        String currentMail = answer.readLine();
        String queryStatus = currentMail.substring(0, 3);

        if (queryStatus.equals(errorMessage)) {
            System.out.println("Error!");
            return;
        }

        if (!havePoint) {
            currentMail = currentMail.substring(3, currentMail.length());
            collection.add(currentMail);
            return;
        } else
            currentMail = answer.readLine();

        while (!currentMail.equals(".")) {
            collection.add(currentMail);
            currentMail = answer.readLine();
        }
    }

    //метод, в котором осуществляется авторизация пользователя
    private void auth() throws IOException, AuthorizationException {
        char[] serverAuthStatus = new char[100];

        answer.read(serverAuthStatus, 0, serverAuthStatus.length);
        if (new String(serverAuthStatus).equals("-ERR" + newLineDelimiter))
            throw new AuthorizationException("Server internal error!");

        //ввод логина
        String authName = "USER " + userName + newLineDelimiter;
        command.write(authName, 0, authName.length());
        command.flush();

        answer.read(serverAuthStatus, 0, serverAuthStatus.length);
        if (new String(serverAuthStatus).equals("-ERR" + newLineDelimiter))
            throw new AuthorizationException("Incorrect login/password couple!");

        //ввод пароля
        String authPass = "PASS " + userPass + newLineDelimiter;
        command.write(authPass, 0, authPass.length());
        command.flush();

        answer.read(serverAuthStatus, 0, serverAuthStatus.length);
        if (new String(serverAuthStatus).substring(0, 4).equals("-ERR"))
            throw new AuthorizationException("Incorrect login/password couple!");
    }

    //метод, который записывает сообщение в файл
    public void writeNewMessageInFile(String fileName) throws Exception {
        waitNewMessage();

        String messageBody = getMessageBody(messageCounter);
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName), Charset.forName("UTF-8"), WRITE, CREATE);
        writer.write(messageBody);
        writer.close();
    }

    //метод в котором ожидаются новые сообщения, если таких нет, то ожидаем время TIMEOUT
    public void waitNewMessage() throws Exception {
        int oldMessageCounter = messageCounter;

        while (true) {
            try {
                connect();
                getMailStat();

                //если сообщений стало больше
                if (messageCounter > oldMessageCounter) {
                    System.out.println("You have new message!");
                    return;
                }

                Thread.sleep(TIMEOUT);
            } catch (InterruptedException e) {
                System.out.println("No have new messages!");
            } catch (AuthorizationException e) {
                e.printStackTrace();
            }
        }
    }
}