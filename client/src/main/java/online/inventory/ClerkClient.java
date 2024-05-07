package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.online.client.grpc.generated.ItemManagerResponse;
import org.online.client.grpc.generated.UpdateItemQuantityRequest;
import org.online.client.grpc.generated.UpdateItemQuantityServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Scanner;

public class ClerkClient {
    private ManagedChannel channel = null;
    UpdateItemQuantityServiceGrpc.UpdateItemQuantityServiceBlockingStub updateItemQuantityStub = null;
    String host = null;
    int port = -1;

    public ClerkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Connecting to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        updateItemQuantityStub = UpdateItemQuantityServiceGrpc.newBlockingStub(channel);
    }

    public void closeConnection() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public void processStockArrivals() throws InterruptedException {
        Scanner userInput = new Scanner(System.in);

        while (true) {
            System.out.println("\nEnter Item code, new quantity:");
            String input[] = userInput.nextLine().trim().split(",");
            String itemCode = input[0];
            int quantity = Integer.parseInt(input[1]);

            UpdateItemQuantityRequest request = UpdateItemQuantityRequest.newBuilder()
                    .setItemCode(itemCode)
                    .setQuantity(quantity)
                    .build();

            ItemManagerResponse response = updateItemQuantityStub.updateItemQuantity(request);

            System.out.println("Update Status: " + (response.getStatus() ? "Successful" : "Failed"));
            System.out.println(response.getMessage() + "\n");

            Thread.sleep(1000);
        }
    }
}
