package ru.itmo.core.main;

import ru.itmo.core.common.classes.MusicBand;
import ru.itmo.core.common.exchange.Client;
import ru.itmo.core.common.exchange.request.ClientRequest;
import ru.itmo.core.common.exchange.response.serverResponse.multidirectional.MultidirectionalResponse;
import ru.itmo.core.common.exchange.response.serverResponse.unidirectional.UnidirectionalResponse;
import ru.itmo.core.connection.ConnectionManager;
import ru.itmo.core.connection.PortForwarder;
import ru.itmo.core.main.handler.ClientRequestHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.*;

public class MainOneThread {


        int serverPort = 44321;

        String dataBaseUrl; // Адрес БД с проброшенным портом
        final String DB_USER = "s284704";
        final String DB_PASS = "hxy284";

        ConcurrentSkipListMap<Integer, MusicBand> collection; // Коллекция

        boolean isRunning;
        final int sendResponsePoolCapacity = 8;

        LinkedBlockingQueue<ClientRequest> requestsQueue
                = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<UnidirectionalResponse> unidirectionalResponsesQueue
                = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<MultidirectionalResponse> multidirectionalResponsesQueue
                = new LinkedBlockingQueue<>();


        ConcurrentSkipListSet<Client> clients
                = new ConcurrentSkipListSet<>();

        ConcurrentLinkedQueue<Connection> connectionsList
                = new ConcurrentLinkedQueue<>(); // Хранит подключения к БД

        ConnectionManager connectionManagerReceiver; // Принимает Request на некоторый порт
        ConnectionManager connectionManagerSender; // Посылает Response с другого порт


        public static void main(String[] args)  {
            new MainMultithreading().initialize(args);
        }


        public void initialize(String[] args) {

            if (args.length > 1) throw new IllegalArgumentException("Error: Gets only server port argument.");
            if (args.length == 1) {
                try {
                    serverPort = java.lang.Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Error: Incorrect server port value. " +
                            "\nMust be: " +
                            "\n    Integer" +
                            "\n    50 000 <= server port <= 60 000");
                }
            }

            connectionManagerReceiver = new ConnectionManager(serverPort);
            connectionManagerSender = new ConnectionManager(serverPort + 1);

            isRunning = true;


            try {

                dataBaseUrl = PortForwarder.forwardAnyPort(); // Пробрасывает порт
                collection = DataBaseManager.loadCollectionFromDataBase(getConnection()); // Загружает коллекцию из БД
                System.out.println("Collection form db was loaded successfully!");

            } catch (Exception e) {
                System.out.println(e.getMessage());
                return;
            }


            runServer();
        }



        public void runServer() {

            System.out.println("Server is running.");

            ForkJoinPool makeResponsePool = new ForkJoinPool();
            ExecutorService sendUnidirectionalResponsePool = Executors.newFixedThreadPool(sendResponsePoolCapacity);
            ExecutorService sendMultidirectionalResponsePool = Executors.newFixedThreadPool(sendResponsePoolCapacity);


            ClientRequestHandler requestHandler = new ClientRequestHandler(new MainMultithreading()); ///
            //////////////////////////////////////////////////*********** // TODO: 26.08.2020  


            Runnable sendUnidirectionalResponseTask = () -> {

                UnidirectionalResponse unidirectionalResponse = unidirectionalResponsesQueue.poll();

                if (unidirectionalResponse == null) return;

                try {
                    connectionManagerSender.sendResponse(unidirectionalResponse);

                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            };


            Runnable sendMultidirectionalResponseTask = () -> {

                MultidirectionalResponse multidirectionalResponse = multidirectionalResponsesQueue.poll();

                if (multidirectionalResponse == null) return;

                try {
                    for (Client client : clients) {
                        multidirectionalResponse.setClient(client);
                        connectionManagerSender.sendResponse(multidirectionalResponse);
                    }

                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }

            };


            Runnable makeResponseTask = () -> {


                ClientRequest requestToProcess = requestsQueue.poll();

                System.out.println("Got a request from requests queue.");
                requestHandler.handle(requestToProcess);

//                sendUnidirectionalResponsePool.submit(sendUnidirectionalResponseTask);

                UnidirectionalResponse unidirectionalResponse = unidirectionalResponsesQueue.poll();

                if (unidirectionalResponse == null) return;

                try {
                    connectionManagerSender.sendResponse(unidirectionalResponse);

                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }


//                sendMultidirectionalResponsePool.submit(sendMultidirectionalResponseTask);

                MultidirectionalResponse multidirectionalResponse = multidirectionalResponsesQueue.poll();

                if (multidirectionalResponse == null) return;

                try {
                    for (Client client : clients) {
                        multidirectionalResponse.setClient(client);
                        connectionManagerSender.sendResponse(multidirectionalResponse);
                    }

                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }

            };


            Runnable receiveRequestTask = () -> {

                ClientRequest newRequest;

                while (isRunning) {
                    try {

                        newRequest = connectionManagerReceiver.receiveRequest();

//
//                        System.out.println("\n\\\\\\\\\\\\\\\\\\\\Request\\\\\\\\\\\\\\\\\\\\");
                        System.out.println(newRequest);


                        requestsQueue.add(newRequest);

//                        makeResponsePool.submit(makeResponseTask);

                        ClientRequest requestToProcess = requestsQueue.poll();

                        System.out.println("Got a request from requests queue.");
                        requestHandler.handle(requestToProcess);

                        UnidirectionalResponse unidirectionalResponse = unidirectionalResponsesQueue.poll();

                        if (unidirectionalResponse == null) return;

                        try {
                            connectionManagerSender.sendResponse(unidirectionalResponse);

                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                        }


//                sendMultidirectionalResponsePool.submit(sendMultidirectionalResponseTask);

                        MultidirectionalResponse multidirectionalResponse = multidirectionalResponsesQueue.poll();

                        if (multidirectionalResponse == null) return;

                        try {
                            for (Client client : clients) {
                                multidirectionalResponse.setClient(client);
                                connectionManagerSender.sendResponse(multidirectionalResponse);
                            }

                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                        }



                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            };


            new Thread(receiveRequestTask).start();

        }


        public boolean addUnidirectionalResponse(UnidirectionalResponse unidirectionalResponse) {
            return unidirectionalResponsesQueue.offer(unidirectionalResponse);
        }

        public boolean addMultidirectionalResponse(MultidirectionalResponse multidirectionalResponse) {
            return multidirectionalResponsesQueue.offer(multidirectionalResponse);
        }


        public synchronized Connection getConnection() {

            if (connectionsList.isEmpty()) {
                try {
                    connectionsList.add(DataBaseManager.connectDataBase(dataBaseUrl, DB_USER, DB_PASS));
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                }
            }

            return connectionsList.poll();
        }


        public synchronized void returnConnection(Connection connection) {
            connectionsList.add(connection);
        }


        public void addClient(Client client) {
            clients.add(client);
        }


        public void removeClient(Client client) {
            clients.remove(client);
        }


        public LinkedBlockingQueue<ClientRequest> getRequestsQueue() {
            return requestsQueue;
        }

        public LinkedBlockingQueue<UnidirectionalResponse> getUnidirectionalResponsesQueue() {
            return unidirectionalResponsesQueue;
        }

        public LinkedBlockingQueue<MultidirectionalResponse> getMultidirectionalResponsesQueue() {
            return multidirectionalResponsesQueue;
        }

        public ConcurrentSkipListMap<java.lang.Integer, MusicBand> getCollection() {
            return collection;
        }
    }




