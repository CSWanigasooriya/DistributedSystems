package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import online.inventory.grpc.generated.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CustomerClient extends BaseClient {
    private static final Logger logger = LoggerFactory.getLogger(CustomerClient.class);

    private ManagedChannel channel = null;
    String host = null;
    int port = -1;

    public CustomerClient() throws IOException, InterruptedException {
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

    public void browseAndReserve() {
        printItems();

        while (true) {
            try {
                System.out.println("\nAvailable items:");
                printItems();

                System.out.println("Enter Item code (type 'exit' to quit):");
                String itemCode = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(itemCode)) {
                    break;
                }

                System.out.println("Enter quantity to reserve:");
                int quantity;
                try {
                    quantity = Integer.parseInt(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("Error: Please enter a valid number for quantity.");
                    logger.error("Number format exception for quantity input", e);
                    continue; // Allow the user to re-enter the correct data
                }

                PlaceOrderRequest request = PlaceOrderRequest.newBuilder().setItemCode(itemCode).setQuantity(quantity).build();

                PlaceOrderResponse response = placeOrderStub.placeOrder(request);

                System.out.println("Reservation Status: " + (response.getStatus() ? "Successful" : "Failed"));
                System.out.println(response.getMessage() + "\n");

                // Ask the user if they want to continue or return to the main menu
                System.out.println("Do you want to reserve another item? (yes/no):");
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
                logger.error("An error occurred during reservation", e);
            }
        }
    }
}