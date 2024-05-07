package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import online.inventory.grpc.generated.GetItemsServiceGrpc;
import online.inventory.grpc.generated.PlaceOrderRequest;
import online.inventory.grpc.generated.PlaceOrderResponse;
import online.inventory.grpc.generated.PlaceOrderServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class WorkshopManagerClient extends BaseClient {
    private static final Logger logger = LoggerFactory.getLogger(WorkshopManagerClient.class);
    private ManagedChannel channel = null;

    String host = null;
    int port = -1;

    public WorkshopManagerClient() throws IOException, InterruptedException {
        super();
        fetchServerDetails();
        initializeStubs();
    }

    @Override
    void fetchServerDetails() throws IOException, InterruptedException {
        NameServiceClient client = new NameServiceClient(NAME_SERVICE_ADDRESS);
        NameServiceClient.ServiceDetails serviceDetails = client.findService(INVENTORY_SERVICE_NAME);
        host = serviceDetails.getIPAddress();
        port = serviceDetails.getPort();
        System.out.println("Connecting to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }


    @Override
    void closeConnection() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    void initializeStubs() {
        getAllItemsStub = GetItemsServiceGrpc.newBlockingStub(channel);
        placeOrderStub = PlaceOrderServiceGrpc.newBlockingStub(channel);
    }

    public void placeOrdersForManufacturing() {
        printItems(); // Display available items for order

        while (true) {
            try {
                System.out.println("\nEnter Item code (type 'exit' to quit):");
                String itemCode = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(itemCode)) {
                    break;
                }

                System.out.println("Enter quantity needed for manufacturing:");
                int quantity;
                try {
                    quantity = Integer.parseInt(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("Error: Please make sure you enter a valid number for quantity.");
                    logger.error("Number format exception", e);
                    continue; // Prompt the user again for input
                }

                PlaceOrderRequest request = PlaceOrderRequest.newBuilder().setItemCode(itemCode).setQuantity(quantity).build();

                PlaceOrderResponse response = placeOrderStub.placeOrder(request);

                System.out.println("Order Placement Status: " + (response.getStatus() ? "Successful" : "Failed"));
                System.out.println(response.getMessage() + "\n");

                // Ask if the user wants to continue placing orders
                System.out.println("Do you want to place another order? (yes/no):");
                String answer = scanner.nextLine().trim();
                if (!"yes".equalsIgnoreCase(answer)) {
                    break; // Exit the loop if the user does not want to continue
                }

                sleep();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Thread was interrupted", e);
                break;
            } catch (Exception e) {
                logger.error("An error occurred during order placement", e);
            }
        }
    }
}