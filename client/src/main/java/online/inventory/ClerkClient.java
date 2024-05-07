package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import online.inventory.grpc.generated.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ClerkClient extends BaseClient {
    private static final Logger logger = LoggerFactory.getLogger(ClerkClient.class);

    private ManagedChannel channel = null;
    String host = null;
    int port = -1;

    public ClerkClient() throws InterruptedException, IOException {
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
        updateItemQuantityStub = UpdateItemQuantityServiceGrpc.newBlockingStub(channel);
    }

    public void processStockArrivals() {
        printItems();

        while (true) {
            System.out.println("\nEnter Item code (type 'exit' to quit):");
            String itemCode = scanner.nextLine().trim();
            if ("exit".equalsIgnoreCase(itemCode)) {
                break;
            }

            System.out.println("Enter new quantity:");
            int quantity;
            try {
                quantity = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Error: Please make sure you enter a valid number for quantity.");
                logger.error("Number format exception", e);
                continue; // Skip processing this input and continue with the next loop iteration
            }

            try {
                UpdateItemQuantityRequest request = UpdateItemQuantityRequest.newBuilder().setItemCode(itemCode).setQuantity(quantity).build();

                ItemManagerResponse response = updateItemQuantityStub.updateItemQuantity(request);
                System.out.println("Update Status: " + (response.getStatus() ? "Successful" : "Failed"));
                System.out.println(response.getMessage() + "\n");

                // Ask the user if they want to continue or return to the main menu
                System.out.println("Do you want to update another item? (yes/no):");
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
                logger.error("An error occurred while updating item quantity", e);
            }
        }
    }


}
