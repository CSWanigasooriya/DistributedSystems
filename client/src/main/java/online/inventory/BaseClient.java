package online.inventory;

import online.inventory.grpc.generated.*;

import java.io.IOException;
import java.util.Scanner;

public abstract class BaseClient {
    public static final Scanner scanner = new Scanner(System.in); // Global scanner

    public static final String NAME_SERVICE_ADDRESS = "http://localhost:2379";
    public static final String INVENTORY_SERVICE_NAME = "InventoryControlService";
    UpdateItemQuantityServiceGrpc.UpdateItemQuantityServiceBlockingStub updateItemQuantityStub = null;
    GetItemsServiceGrpc.GetItemsServiceBlockingStub getAllItemsStub = null;
    PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub placeOrderStub = null;
    AddItemServiceGrpc.AddItemServiceBlockingStub addItemStub = null;
    UpdateItemServiceGrpc.UpdateItemServiceBlockingStub updateItemStub = null;
    DeleteItemServiceGrpc.DeleteItemServiceBlockingStub deleteItemStub = null;

    abstract void fetchServerDetails() throws IOException, InterruptedException;
    abstract void initializeStubs();
    abstract void closeConnection();

    protected void sleep() throws InterruptedException {
        Thread.sleep(1000);
    }

    protected void printItems() {
        GetItemsRequest request = GetItemsRequest.newBuilder().build();
        GetItemsResponse response = getAllItemsStub.getItems(request);

        System.out.println("##########################################");
        response.getItemsList().forEach(item -> System.out.printf("Item: itemCode = %s, itemName = %s, quantity = %d, unitPrice = %.2f%n", item.getItemCode(), item.getItemName(), item.getQuantity(), item.getUnitPrice()));
        System.out.println("##########################################");
    }

}
