package org.example;

import com.google.protobuf.util.JsonFormat;
import org.example.protocol.Chat.ChatMessage;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;

    private final String username;
    private final ExecutorService threadPool;
    private Socket socket;
    private PrintWriter writer;
    private Scanner reader;
    private final Scanner consoleScanner;
    private final JsonFormat.Parser jsonParser;

    public Client(String username) {
        this.username = username;
        threadPool = Executors.newFixedThreadPool(5);
        consoleScanner = new Scanner(System.in);
        jsonParser = JsonFormat.parser().ignoringUnknownFields();
    }

    public void connect() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            writer = new PrintWriter(socket.getOutputStream());
            reader = new Scanner(socket.getInputStream());

            threadPool.execute(new MessageReceiver());
            writer.println(username);
            writer.flush();
            System.out.println("Connected to server.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String sender, String receiver, String message) {
        ChatMessage chatMsg = ChatMessage.newBuilder()
                .setSender(sender)
                .setReceiver(receiver)
                .setMessage(message)
                .build();

        String json = "";
        try {
            json = JsonFormat.printer().print(chatMsg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Your message has been successfully sent!");

        writer.println(json);
        writer.flush();
    }

    public void sendMessageFromConsole() {
        System.out.print("Enter receiver's username: ");
        String receiverUsername = consoleScanner.nextLine();
        System.out.print("Enter message: ");
        String message = consoleScanner.nextLine();
        sendMessage(username, receiverUsername, message);
    }

    public void shutdown() {
        try {
            threadPool.shutdown();
            writer.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your username: ");
        String username = scanner.nextLine();

        Client client = new Client(username);
        client.connect();

        while (true) {
            client.sendMessageFromConsole();
            System.out.print("Do you want to send another message? (y/n): ");
            String choice = scanner.nextLine();
            if (choice.equalsIgnoreCase("n")) {
                break;
            }
        }

        client.shutdown();
        scanner.close();
    }

    private class MessageReceiver implements Runnable {
        private final StringBuilder messageBuffer;

        public MessageReceiver() {
            messageBuffer = new StringBuilder();
        }

        @Override
        public void run() {
            try {
                while (!socket.isClosed() && reader.hasNextLine()) {
                    String receivedMessage = reader.nextLine();
                    messageBuffer.append(receivedMessage);

                    // Check if the message is complete (ends with '}')
                    if (receivedMessage.endsWith("}")) {
                        String completeMessage = messageBuffer.toString();
                        messageBuffer.setLength(0); // Clear the message buffer
                        ChatMessage.Builder chatMsgBuilder = ChatMessage.newBuilder();
                        jsonParser.merge(completeMessage, chatMsgBuilder);
                        ChatMessage chatMsg = chatMsgBuilder.build();
                        String sender = chatMsg.getSender();
                        String receiver = chatMsg.getReceiver();
                        String message = chatMsg.getMessage();
                        if (receiver.equals(username) || receiver.equals("all")) {
                            System.out.println("Received message from " + sender + ": " + message);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                shutdown();
            }
        }
    }
}
