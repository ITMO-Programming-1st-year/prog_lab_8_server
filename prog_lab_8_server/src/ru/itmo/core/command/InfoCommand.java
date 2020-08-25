package ru.itmo.core.command;

import ru.itmo.core.common.classes.MusicBand;
import ru.itmo.core.common.exchange.Client;
import ru.itmo.core.common.exchange.request.clientRequest.userCommandRequest.InsertCommandRequest;
import ru.itmo.core.common.exchange.response.serverResponse.unidirectional.userResponse.GeneralResponse;
import ru.itmo.core.common.exchange.response.serverResponse.unidirectional.userResponse.UserCommandResponseStatus;
import ru.itmo.core.main.MainMultithreading;

import java.util.concurrent.ConcurrentSkipListMap;

public class InfoCommand extends Command {


    private MainMultithreading main;
    private ConcurrentSkipListMap<Integer, MusicBand> collection;

    public InfoCommand(MainMultithreading main) {
        setMain(main);
        setCollection(main.getCollection());
    }

    public static String syntaxDescription =
                    "\nCommand: info" +
                    "\nDescription: Prints the information about collection." +
                    "\nNumber of arguments: 0\n";


    public void execute(InsertCommandRequest request) {

        Client client = request.getClient();

        String info =
                String.format(
                        "Collection type: TreeMap" +
                        "\nCollection consists of pairs:" +
                        "\n   Key:   Integer" +
                        "\n   Value: MusicBand" +
                        "\nNumber of elements: %s",
                          collection.size()
                );

        GeneralResponse generalResponse = new GeneralResponse(
                client,
                UserCommandResponseStatus.CANCEL,
                info
        );

        main.addUnidirectionalResponse(generalResponse);

    }


    public void setMain(MainMultithreading main) {

        if (main == null)
            throw new IllegalArgumentException("Invalid main value : 'null'.");

        this.main = main;
    }


    public void setCollection(ConcurrentSkipListMap<java.lang.Integer, MusicBand> collection) {

        if (collection == null)
            throw new IllegalArgumentException("Invalid collection value : 'null'");

        this.collection = collection;
    }

}
