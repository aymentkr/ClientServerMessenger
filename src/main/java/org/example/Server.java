package org.example;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.example.protocol.Chat.ChatMessage;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server {
    private final Map<String, ClientHandler> clients;
    private final Queue<String> messageQueue;
    private final JsonFormat.Parser jsonParser;

    public Server() {
        clients = new HashMap<>();
        messageQueue = new LinkedList<>();
        jsonParser = JsonFormat.parser().ignoringUnknownFields();
    }

    public void start(int port) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);

            // Create a separate thread for reading console input
            Thread consoleThread = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    String input = scanner.nextLine();
                    if (input.startsWith("/all ")) {
                        String message = input.substring(5); // Remove "/all " prefix
                        sendMessageToClient("Server", "all", message);
                    }
                }
            });
            consoleThread.start();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                // Create a new thread to handle the client connection
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessageToClient(String sender, String receiver, String message) {
        if (receiver.equals("all")) {
            for (ClientHandler client : clients.values()) {
                client.sendMessage(sender, receiver, message);
            }
        } else {
            ClientHandler client = clients.get(receiver);
            if (client != null) {
                client.sendMessage(sender, receiver, message);
            } else {
                // Add the message to the queue for offline clients
                messageQueue.add(receiver + ":" + sender + ":" + message);
            }
        }
    }


    private void sendQueuedMessages(ClientHandler clientHandler) {
        String username = clientHandler.getUsername();

        Iterator<String> iterator = messageQueue.iterator();
        while (iterator.hasNext()) {
            String queuedMessage = iterator.next();
            String[] parts = queuedMessage.split(":", 3);
            String receiver = parts[0];
            String sender = parts[1];
            String message = parts[2];

            if (receiver.equals(username)) {
                clientHandler.sendMessage(sender, receiver, message);
                iterator.remove();
            }
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start(1234);
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter writer;
        private Scanner reader;
        private String username;
        private final StringBuilder messageBuffer;

        public ClientHandler(Socket socket) {
            clientSocket = socket;
            messageBuffer = new StringBuilder();
            try {
                writer = new PrintWriter(clientSocket.getOutputStream());
                reader = new Scanner(clientSocket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public String getUsername() {
            return username;
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
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            writer.println(json);
            writer.flush();
        }

        @Override
        public void run() {
            if (reader.hasNextLine()) {
                username = reader.nextLine();
                clients.put(username, this);
                sendQueuedMessages(this);
            }

            try {
                while (true) {
                    if (reader.hasNextLine()) {
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

                            System.out.println("Received message from " + sender + " to " + receiver + ": " + message);
                            sendMessageToClient(sender, receiver, message);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                clients.remove(username);
                try {
                    writer.close();
                    reader.close();
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
