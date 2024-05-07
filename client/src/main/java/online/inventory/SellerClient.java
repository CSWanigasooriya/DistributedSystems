package online.inventory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.online.client.grpc.generated.*;

public class SellerClient {
    private ManagedChannel channel = null;
    private AddItemServiceGrpc.AddItemServiceBlockingStub addItemStub = null;
    private UpdateItemServiceGrpc.UpdateItemServiceBlockingStub updateItemStub = null;
    private DeleteItemServiceGrpc.DeleteItemServiceBlockingStub deleteItemStub = null;
    private String host = null;
    private int port = -1;

    public SellerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void initializeConnection() {
        System.out.println("Connecting to server at " + host + ":" + port);
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        addItemStub = AddItemServiceGrpc.newBlockingStub(channel);
        updateItemStub = UpdateItemServiceGrpc.newBlockingStub(channel);
        deleteItemStub = DeleteItemServiceGrpc.newBlockingStub(channel);
    }

    public void closeConnection() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public void addItem(String itemCode, String itemName, int quantity, double unitPrice) {
        AddItemRequest request = AddItemRequest.newBuilder().setItemCode(itemCode).setItemName(itemName).setQuantity(quantity).setUnitPrice(unitPrice).build();
        ItemManagerResponse response = addItemStub.addItem(request);
        System.out.println("Add Item Response: " + response.getMessage());
    }

    public void updateItem(String itemCode, String itemName, int quantity, double unitPrice) {
        UpdateItemRequest request = UpdateItemRequest.newBuilder().setItemCode(itemCode).setItemName(itemName).setQuantity(quantity).setUnitPrice(unitPrice).build();
        ItemManagerResponse response = updateItemStub.updateItem(request);
        System.out.println("Update Item Response: " + response.getMessage());
    }

    public void deleteItem(String itemCode) {
        DeleteItemRequest request = DeleteItemRequest.newBuilder().setItemCode(itemCode).build();
        ItemManagerResponse response = deleteItemStub.deleteItem(request);
        System.out.println("Delete Item Response: " + response.getMessage());
    }
}