package online.inventory;

import io.grpc.stub.StreamObserver;
import online.inventory.grpc.generated.Item;
import online.inventory.grpc.generated.ItemManagerResponse;
import online.inventory.grpc.generated.UpdateItemRequest;
import online.inventory.grpc.generated.UpdateItemServiceGrpc;


public class UpdateItemService extends UpdateItemServiceGrpc.UpdateItemServiceImplBase implements DistributedTxListener{
    private final InventoryControlServer inventoryControlServer;

    public UpdateItemService(InventoryControlServer inventoryControlServer) {
        this.inventoryControlServer = inventoryControlServer;
    }

    @Override
    public void updateItem(UpdateItemRequest request, StreamObserver<ItemManagerResponse> responseObserver) {
        String itemCode = request.getItemCode();
        Item existingItem = inventoryControlServer.getItemWithCode(itemCode);

        if (existingItem != null) {
            // Update item details
            Item updatedItem = existingItem.toBuilder()
                    .setItemName(request.getItemName())
                    .setQuantity(request.getQuantity())
                    .setUnitPrice(request.getUnitPrice())
                    .build();

            inventoryControlServer.setItemWithCode(itemCode, updatedItem);

            ItemManagerResponse response = ItemManagerResponse.newBuilder()
                    .setItemName(updatedItem.getItemName())
                    .setMessage("Item updated successfully.")
                    .setStatus(true)
                    .build();

            responseObserver.onNext(response);
        } else {
            ItemManagerResponse response = ItemManagerResponse.newBuilder()
                    .setMessage("Item not found.")
                    .setStatus(false)
                    .build();

            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }

    @Override
    public void onGlobalCommit() {

    }

    @Override
    public void onGlobalAbort() {

    }
}