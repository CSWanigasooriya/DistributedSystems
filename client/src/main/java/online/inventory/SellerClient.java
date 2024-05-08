package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import online.inventory.grpc.generated.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class SellerClient extends BaseClient {
    private static final Logger logger = LoggerFactory.getLogger(SellerClient.class);
    private ManagedChannel channel = null;

    String host = null;
    int port = -1;

    public SellerClient() throws IOException, InterruptedException {
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
        addItemStub = AddItemServiceGrpc.newBlockingStub(channel);
        updateItemStub = UpdateItemServiceGrpc.newBlockingStub(channel);
        deleteItemStub = DeleteItemServiceGrpc.newBlockingStub(channel);
    }

    public void viewItems() {
        printItems();
    }

    public void addItem() {
        printItems();

        try {
            System.out.println("Enter item code:");
            String itemCode = scanner.nextLine().trim();
            System.out.println("Enter item name:");
            String itemName = scanner.nextLine().trim();
            System.out.println("Enter quantity:");
            int quantity = Integer.parseInt(scanner.nextLine().trim());
            System.out.println("Enter unit price:");
            double unitPrice = Double.parseDouble(scanner.nextLine().trim());

            AddItemRequest request = AddItemRequest.newBuilder().setItemCode(itemCode).setItemName(itemName).setQuantity(quantity).setUnitPrice(unitPrice).build();
            ItemManagerResponse response = addItemStub.addItem(request);
            System.out.println("Add Item Response: " + response.getMessage());
        } catch (NumberFormatException e) {
            logger.error("Error: Please enter valid numeric values for quantity and unit price.");
        } // No need to close the scanner here, as it's used globally
    }

    public void updateItem() {
        printItems();

        try {
            System.out.println("Enter item code:");
            String itemCode = scanner.nextLine().trim();
            System.out.println("Enter item name:");
            String itemName = scanner.nextLine().trim();
            System.out.println("Enter quantity:");
            int quantity = Integer.parseInt(scanner.nextLine().trim());
            System.out.println("Enter unit price:");
            double unitPrice = Double.parseDouble(scanner.nextLine().trim());

            UpdateItemRequest request = UpdateItemRequest.newBuilder().setItemCode(itemCode).setItemName(itemName).setQuantity(quantity).setUnitPrice(unitPrice).build();
            ItemManagerResponse response = updateItemStub.updateItem(request);
            System.out.println("Update Item Response: " + response.getMessage());
        } catch (NumberFormatException e) {
            logger.error("Error: Please enter valid numeric values for quantity and unit price.");
            logger.error("Number format exception", e);
        } catch (Exception e) {
            logger.error("An error occurred while updating item", e);
        }
    }

    public void deleteItem() {
        printItems();

        try {
            System.out.println("Enter item code:");
            String itemCode = scanner.nextLine().trim();

            DeleteItemRequest request = DeleteItemRequest.newBuilder().setItemCode(itemCode).build();
            ItemManagerResponse response = deleteItemStub.deleteItem(request);
            System.out.println("Delete Item Response: " + response.getMessage());
        } catch (Exception e) {
            logger.error("An error occurred while deleting item", e);
        }
    }
}