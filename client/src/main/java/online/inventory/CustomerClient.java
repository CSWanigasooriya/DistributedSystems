package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.online.client.grpc.generated.*;

import java.util.Scanner;

public class CustomerClient {
    private ManagedChannel channel = null;
    GetItemsServiceGrpc.GetItemsServiceBlockingStub getAllItemsStub = null;
    PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub placeOrderStub = null;
    String host = null;
    int port = -1;

    public CustomerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Connecting to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        getAllItemsStub = GetItemsServiceGrpc.newBlockingStub(channel);
        placeOrderStub = PlaceOrderServiceGrpc.newBlockingStub(channel);
    }

    public void closeConnection() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public void browseAndReserve() throws InterruptedException {
        Scanner userInput = new Scanner(System.in);

        while (true) {
            System.out.println("\nAvailable items:");
            printItems();

            System.out.println("\nEnter Item code, quantity to reserve:");
            String input[] = userInput.nextLine().trim().split(",");
            String itemCode = input[0];
            int quantity = Integer.parseInt(input[1]);

            PlaceOrderRequest request = PlaceOrderRequest.newBuilder().setItemCode(itemCode).setQuantity(quantity).build();

            PlaceOrderResponse response = placeOrderStub.placeOrder(request);

            System.out.println("Reservation Status: " + (response.getStatus() ? "Successful" : "Failed"));
            System.out.println(response.getMessage() + "\n");

            Thread.sleep(1000);
        }
    }

    private void printItems() {
        GetItemsRequest request = GetItemsRequest.newBuilder().build();
        GetItemsResponse response = getAllItemsStub.getItems(request);

        System.out.println("##########################################");
        response.getItemsList().forEach(item -> System.out.println(String.format("Item: itemCode = %s, itemName = %s, quantity = %d, unitPrice = %.2f", item.getItemCode(), item.getItemName(), item.getQuantity(), item.getUnitPrice())));
        System.out.println("##########################################");
    }
}