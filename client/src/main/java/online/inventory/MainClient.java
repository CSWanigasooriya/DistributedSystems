package online.inventory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainClient {
    private static final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    private static final CredentialStore credentialStore = new CredentialStore();

    public static void main(String[] args) {
        try {
            boolean running = true;
            while (running) {
                System.out.println("\nSelect the client role to perform operations:");
                System.out.println("1. Seller Client");
                System.out.println("2. Clerk Client");
                System.out.println("3. Customer Client");
                System.out.println("4. Workshop Manager Client");
                System.out.println("5. Exit");

                System.out.println("Enter your choice (1-5):");
                String option = reader.readLine();

                switch (option) {
                    case "1":
                        handleSellerClient();
                        break;
                    case "2":
                        handleClerkClient();
                        break;
                    case "3":
                        handleCustomerClient();
                        break;
                    case "4":
                        handleWorkshopManagerClient();
                        break;
                    case "5":
                        running = false; // Exit loop
                        System.out.println("Exiting program...");
                        break;
                    default:
                        System.out.println("Invalid option. Please select a valid client role.");
                        break;
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("IO error: " + e.getMessage());
        }
    }

    private static boolean authenticateSeller() throws IOException {
        System.out.print("Enter Username: ");
        String username = reader.readLine().trim();
        System.out.print("Enter Password: ");
        String password = reader.readLine().trim();

        if (credentialStore.validateCredentials(username, password)) {
            System.out.println("Authentication Successful.");
            return true;
        } else {
            System.out.println("Authentication Failed. Please try again.");
            return false;
        }
    }

    private static void handleSellerClient() throws IOException, InterruptedException {
        System.out.println("Please log in to continue.");
        if (!authenticateSeller()) {
            return;  // Return to the main menu if authentication fails
        }

        SellerClient sellerClient = new SellerClient();
        System.out.println("Connected to Seller Client services.");

        boolean done = false;
        while (!done) {
            System.out.println("\nSelect Action:");
            System.out.println("1. View Items");
            System.out.println("2. Add Item");
            System.out.println("3. Update Item");
            System.out.println("4. Delete Item");
            System.out.println("5. Return to Main Menu");

            System.out.print("Enter your choice (1-5): ");
            String action = reader.readLine();

            switch (action) {
                case "1":
                    System.out.println("Viewing All Items...");
                    sellerClient.viewItems();
                    break;
                case "2":
                    System.out.println("Performing Add Item...");
                    sellerClient.addItem();
                    break;
                case "3":
                    System.out.println("Performing Update Item...");
                    sellerClient.updateItem();
                    break;
                case "4":
                    System.out.println("Performing Delete Item...");
                    sellerClient.deleteItem();
                    break;
                case "5":
                    System.out.println("Returning to Main Menu...");
                    done = true;
                    break;
                default:
                    System.out.println("Invalid option. Please select a valid action.");
                    break;
            }
        }
        sellerClient.closeConnection();
        System.out.println("Disconnected from Seller Client services.");
    }

    private static void handleClerkClient() throws IOException, InterruptedException {
        ClerkClient clerkClient = new ClerkClient();
        System.out.println("Performing actions for Clerk Client...");
        clerkClient.processStockArrivals();
        clerkClient.closeConnection();
    }

    private static void handleCustomerClient() throws IOException, InterruptedException {
        CustomerClient customerClient = new CustomerClient();
        System.out.println("Performing actions for Customer Client...");
        customerClient.browseAndReserve();
        customerClient.closeConnection();
    }

    private static void handleWorkshopManagerClient() throws IOException, InterruptedException {
        WorkshopManagerClient managerClient = new WorkshopManagerClient();
        System.out.println("Performing actions for Workshop Manager Client...");
        managerClient.placeOrdersForManufacturing();
        managerClient.closeConnection();
    }
}