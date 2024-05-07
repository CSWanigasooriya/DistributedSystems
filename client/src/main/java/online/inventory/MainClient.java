package online.inventory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Select the client role to perform operations:");
        System.out.println("1. Seller Client");
        System.out.println("2. Clerk Client");
        System.out.println("3. Customer Client");
        System.out.println("4. Workshop Manager Client");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter your choice (1-4):");
        String option = reader.readLine();

        switch (option) {
            case "1":
                SellerClient sellerClient = new SellerClient("localhost", 9201);
                sellerClient.initializeConnection();
                System.out.println("Performing actions for Seller Client...");
                // Example operations for the seller
                sellerClient.addItem("004", "High-Quality Widget", 10, 15.75);
                sellerClient.updateItem("004", "Superior Quality Widget", 15, 18.00);
                sellerClient.deleteItem("004");
                sellerClient.closeConnection();
                break;
            case "2":
                ClerkClient clerkClient = new ClerkClient("localhost", 9201);
                clerkClient.initializeConnection();
                System.out.println("Performing actions for Clerk Client...");
                // Example operation for the clerk
                clerkClient.processStockArrivals();
                clerkClient.closeConnection();
                break;
            case "3":
                CustomerClient customerClient = new CustomerClient("localhost", 9201);
                customerClient.initializeConnection();
                System.out.println("Performing actions for Customer Client...");
                // Example operations for the customer
                customerClient.browseAndReserve();
                customerClient.closeConnection();
                break;
            case "4":
                WorkshopManagerClient managerClient = new WorkshopManagerClient("localhost", 9201);
                managerClient.initializeConnection();
                System.out.println("Performing actions for Workshop Manager Client...");
                // Example operation for the workshop manager
                managerClient.placeOrdersForManufacturing();
                managerClient.closeConnection();
                break;
            default:
                System.out.println("Invalid option. Please select a valid client role.");
                break;
        }
    }
}