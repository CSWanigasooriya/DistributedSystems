package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.online.client.grpc.generated.PlaceOrderRequest;
import org.online.client.grpc.generated.PlaceOrderResponse;
import org.online.client.grpc.generated.PlaceOrderServiceGrpc;

import java.util.Scanner;

public class WorkshopManagerClient {
    private ManagedChannel channel = null;
    PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub placeOrderStub = null;
    String host = null;
    int port = -1;

    public WorkshopManagerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Connecting to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        placeOrderStub = PlaceOrderServiceGrpc.newBlockingStub(channel);
    }

    public void closeConnection() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public void placeOrdersForManufacturing() throws InterruptedException {
        Scanner userInput = new Scanner(System.in);

        while (true) {
            System.out.println("\nEnter Item code, quantity needed for manufacturing:");
            String input[] = userInput.nextLine().trim().split(",");
            String itemCode = input[0];
            int quantity = Integer.parseInt(input[1]);

            PlaceOrderRequest request = PlaceOrderRequest.newBuilder()
                    .setItemCode(itemCode)
                    .setQuantity(quantity)
                    .build();

            PlaceOrderResponse response = placeOrderStub.placeOrder(request);

            System.out.println("Order Placement Status: " + (response.getStatus() ? "Successful" : "Failed"));
            System.out.println(response.getMessage() + "\n");

            Thread.sleep(1000);
        }
    }
}