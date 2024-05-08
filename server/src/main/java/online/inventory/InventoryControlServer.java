package online.inventory;


import io.grpc.Server;
import io.grpc.ServerBuilder;
import online.inventory.grpc.generated.Item;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class InventoryControlServer {
    public static final String NAME_SERVICE_ADDRESS = "http://localhost:2379";
    private static final String ZOOKEEPER_URL = "localhost:2181";
    private final DistributedLock leaderLock;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private byte[] leaderData;
    private final int serverPort;
    private static final Map<String, Item> inventory = new HashMap<>();
    private AddItemService addItemService;
    private UpdateItemService updateItemService;
    private DeleteItemService deleteItemService;
    private PlaceOrderService placeOrderService;

    // Helper class to store item type data
    private static class ItemData {
        String name;
        double basePrice;

        ItemData(String name, double basePrice) {
            this.name = name;
            this.basePrice = basePrice;
        }
    }

    DistributedTx transaction;
    GetItemsService getItemsService;
    UpdateItemQuantityService updateQuantityService;

    List<DistributedTxListener> txListeners;


    public InventoryControlServer(String host, int port) throws InterruptedException, IOException, KeeperException {
        this.serverPort = port;
        this.leaderLock = new DistributedLock("InventoryControlServerCluster", buildServerData(host, port));
        txListeners = new ArrayList<>();

        initializeServices();
        initializeTransactionListeners();

        populateItems();
        printInventory();
    }

    private void initializeServices() {
        this.getItemsService = new GetItemsService(this);
        this.updateQuantityService = new UpdateItemQuantityService(this);
        this.addItemService = new AddItemService(this);
        this.updateItemService = new UpdateItemService(this);
        this.deleteItemService = new DeleteItemService(this);
        this.placeOrderService = new PlaceOrderService(this);
    }

    private void initializeTransactionListeners() {
        // Assuming all service implementations are designed to handle transaction events
        txListeners.add(getItemsService);
        txListeners.add(updateQuantityService);
        txListeners.add(addItemService);
        txListeners.add(updateItemService);
        txListeners.add(deleteItemService);
        txListeners.add(placeOrderService);

        // Initialize the transaction coordinator or participant with the list of listeners
        this.transaction = new DistributedTxParticipant(txListeners);
    }

    private void populateItems() {
        Random rand = new Random();

        // Define a list of potential items with base names and base prices
        List<ItemData> itemTypes = Arrays.asList(new ItemData("Printer Paper", 1.50), new ItemData("Stapler", 4.00), new ItemData("Notebook", 2.00), new ItemData("Pen", 0.50), new ItemData("Pencil", 0.30));

        // Populate the inventory with randomized items
        for (int i = 0; i < 10; i++) { // Generate 10 random items
            ItemData type = itemTypes.get(rand.nextInt(itemTypes.size())); // Randomly select an item type
            int quantity = rand.nextInt(100) + 1; // Random quantity between 1 and 100
            double priceVariation = rand.nextDouble(); // Random price variation between 0.0 and 1.0
            double unitPrice = type.basePrice + priceVariation; // Adjust base price by random variation

            Item item = Item.newBuilder().setItemCode(String.format("%03d", i + 1)) // Generate item codes like "001", "002", etc.
                    .setItemName(type.name).setQuantity(quantity).setUnitPrice(Math.round(unitPrice * 100.0) / 100.0) // Round to 2 decimal places
                    .build();

            inventory.put(item.getItemCode(), item);
        }
    }

    public static String buildServerData(String ip, int port) {
        return ip + ":" + port;
    }

    public DistributedTx getTransaction() {
        return transaction;
    }

    private void tryToBeLeader() {
        Thread leaderCampaignThread = new Thread(new LeaderCampaignThread());
        leaderCampaignThread.start();
    }

    public void startServer() throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(serverPort).addService(getItemsService).addService(updateQuantityService).addService(addItemService).addService(updateItemService).addService(deleteItemService).addService(placeOrderService).build();

        server.start();
        System.out.println("InventoryControlServer Started and ready to accept requests on port " + serverPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            if (isLeader.get()) {
                try {
                    leaderLock.releaseLock();
                    System.out.println("Lock released due to server shutdown");
                } catch (Exception e) {
                    System.out.println("Failed to release the leader lock on shutdown");
                    e.printStackTrace();
                }
            }
            server.shutdown();
            System.out.println("Server shutdown complete");
        }));

        tryToBeLeader();

        NameServiceClient client = new NameServiceClient(NAME_SERVICE_ADDRESS);
        client.registerService("InventoryControlService", "127.0.0.1", serverPort, "tcp");

        server.awaitTermination();
    }

    public boolean isLeader() {
        return isLeader.get();
    }

    private synchronized void setCurrentLeaderData(byte[] leaderData) {
        this.leaderData = leaderData;
    }

    public synchronized String[] getCurrentLeaderData() {
        return new String(leaderData).split(":");
    }

    public void setItemWithCode(String itemCode, Item item) {
        inventory.put(itemCode, item);
    }

    public Item getItemWithCode(String itemCode) {
        return inventory.get(itemCode);
    }

    public void removeItem(String itemCode) {
        inventory.remove(itemCode);
    }

    public List<Item> getAllItems() {
        return new ArrayList<>(inventory.values());
    }

    public List<String[]> getOthersData() throws KeeperException, InterruptedException {
        List<String[]> result = new ArrayList<>();
        List<byte[]> othersData = leaderLock.getOthersData();

        for (byte[] data : othersData) {
            String[] dataStrings = new String(data).split(":");
            result.add(dataStrings);
        }
        return result;
    }

    class LeaderCampaignThread implements Runnable {
        private byte[] currentLeaderData = null;

        @Override
        public void run() {
            System.out.println("Starting the leader campaign");
            try {
                boolean leader = leaderLock.tryAcquireLock();
                while (!leader) {
                    byte[] leaderData = leaderLock.getLockHolderData();
                    if (currentLeaderData != leaderData) {
                        currentLeaderData = leaderData;
                        setCurrentLeaderData(currentLeaderData);
                    }
                    Thread.sleep(10000); // Retry every 10 seconds
                    leader = leaderLock.tryAcquireLock();
                }
                currentLeaderData = null;
                beTheLeader();
            } catch (InterruptedException e) {
                System.out.println("Leader campaign interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    leaderLock.releaseLock(); // Ensure the lock is released if the thread exits
                } catch (KeeperException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void beTheLeader() {
        System.out.println("I got the leader lock. Now acting as primary");
        isLeader.set(true);
        transaction = new DistributedTxCoordinator(txListeners);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage executable-name <port>");
        }

        int serverPort = Integer.parseInt(args[0]);
        DistributedLock.setZooKeeperURL(ZOOKEEPER_URL);
        DistributedTx.setZooKeeperURL(ZOOKEEPER_URL);

        InventoryControlServer server = new InventoryControlServer("localhost", serverPort);
        server.startServer();
    }

    public void printInventory() {
        System.out.println("##########################################");
        for (Item item : inventory.values()) {
            String output = String.format("Item: itemCode = %s, itemName = %s, quantity = %d, unitPrice = %.2f", item.getItemCode(), item.getItemName(), item.getQuantity(), item.getUnitPrice());
            System.out.println(output);
        }
        System.out.println("##########################################");
    }
}
